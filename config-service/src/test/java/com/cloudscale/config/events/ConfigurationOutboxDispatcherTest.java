package com.cloudscale.config.events;

import com.cloudscale.contracts.configuration.ConfigurationEntityType;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.configuration.ConfigurationEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurationOutboxDispatcherTest {
    private ConfigurationOutboxRepository repository;
    private KafkaTemplate<String, ConfigurationEvent> kafkaTemplate;
    private ObjectMapper objectMapper;
    private ConfigurationOutboxDispatcher dispatcher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(ConfigurationOutboxRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        dispatcher = new ConfigurationOutboxDispatcher(
                repository, kafkaTemplate, objectMapper, new SimpleMeterRegistry());
    }

    @Test
    void marksOutboxRowPublishedOnlyAfterKafkaAcknowledges() throws Exception {
        ConfigurationEvent event = event();
        ConfigurationOutboxRepository.PendingEvent pending = pending(event, 0);
        when(repository.lockNextBatch(anyInt(), any(Instant.class))).thenReturn(List.of(pending));
        when(objectMapper.readValue(pending.payload(), ConfigurationEvent.class)).thenReturn(event);
        when(kafkaTemplate.send(anyString(), anyString(), any(ConfigurationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        dispatcher.dispatchPending();

        verify(kafkaTemplate).send("cloudscale.configuration-events", pending.messageKey(), event);
        verify(repository).markPublished(eq(event.eventId()), any(Instant.class));
        verify(repository, never()).scheduleRetry(any(), anyInt(), any(), anyString());
    }

    @Test
    void schedulesRetryWhenKafkaPublishFails() throws Exception {
        ConfigurationEvent event = event();
        ConfigurationOutboxRepository.PendingEvent pending = pending(event, 0);
        when(repository.lockNextBatch(anyInt(), any(Instant.class))).thenReturn(List.of(pending));
        when(objectMapper.readValue(pending.payload(), ConfigurationEvent.class)).thenReturn(event);
        when(kafkaTemplate.send(anyString(), anyString(), any(ConfigurationEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        dispatcher.dispatchPending();

        verify(repository).scheduleRetry(eq(event.eventId()), eq(1), any(Instant.class),
                eq("IllegalStateException"));
        verify(repository, never()).markPublished(any(), any());
    }

    private ConfigurationOutboxRepository.PendingEvent pending(ConfigurationEvent event, int attempts) {
        return new ConfigurationOutboxRepository.PendingEvent(event.eventId(), event.featureKey(),
                "serialized-event", attempts);
    }

    private ConfigurationEvent event() {
        return new ConfigurationEvent(
                UUID.randomUUID(),
                ConfigurationEventType.CONFIGURATION_PUBLISHED,
                2,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConfigurationEntityType.FEATURE_VERSION,
                "config-service",
                "trace-123",
                UUID.randomUUID(),
                "project-1",
                UUID.randomUUID(),
                "env-1",
                UUID.randomUUID(),
                "feature-1",
                UUID.randomUUID(),
                1,
                4L,
                true);
    }
}
