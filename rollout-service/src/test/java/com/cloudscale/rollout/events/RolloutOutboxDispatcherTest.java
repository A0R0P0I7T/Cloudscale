package com.cloudscale.rollout.events;

import com.cloudscale.contracts.rollout.RolloutEvent;
import com.cloudscale.contracts.rollout.RolloutEventType;
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

class RolloutOutboxDispatcherTest {
    private RolloutOutboxRepository repository;
    private KafkaTemplate<String, RolloutEvent> kafkaTemplate;
    private ObjectMapper objectMapper;
    private RolloutOutboxDispatcher dispatcher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(RolloutOutboxRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        dispatcher = new RolloutOutboxDispatcher(
                repository, kafkaTemplate, objectMapper, new SimpleMeterRegistry());
    }

    @Test
    void marksRolloutEventPublishedAfterKafkaAcknowledgement() throws Exception {
        RolloutEvent event = event();
        RolloutOutboxRepository.PendingEvent pending = pending(event);
        when(repository.lockNextBatch(anyInt(), any(Instant.class))).thenReturn(List.of(pending));
        when(objectMapper.readValue(pending.payload(), RolloutEvent.class)).thenReturn(event);
        when(kafkaTemplate.send(anyString(), anyString(), any(RolloutEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        dispatcher.dispatchPending();

        verify(repository).markPublished(eq(event.eventId()), any(Instant.class));
        verify(repository, never()).scheduleRetry(any(), anyInt(), any(), anyString());
    }

    private RolloutOutboxRepository.PendingEvent pending(RolloutEvent event) {
        return new RolloutOutboxRepository.PendingEvent(event.eventId(), "org:rollout", "event-json", 0);
    }

    private RolloutEvent event() {
        return new RolloutEvent(UUID.randomUUID(), RolloutEventType.ROLLOUT_STARTED, 1, Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), "ROLLOUT", "rollout-service", "trace-1",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 25, "IN_PROGRESS");
    }
}
