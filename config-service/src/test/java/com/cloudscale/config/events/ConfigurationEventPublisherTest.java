package com.cloudscale.config.events;

import com.cloudscale.contracts.configuration.ConfigurationEntityType;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.configuration.ConfigurationEventType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurationEventPublisherTest {

    private KafkaTemplate<String, ConfigurationEvent> kafkaTemplate;
    private ConfigurationEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new ConfigurationEventPublisher(kafkaTemplate, new SimpleMeterRegistry());
    }

    @Test
    void publish_sendsEventWithCorrectKey() {
        UUID orgId = UUID.randomUUID();
        ConfigurationEvent event = new ConfigurationEvent(
                UUID.randomUUID(),
                ConfigurationEventType.CONFIGURATION_PUBLISHED,
                2,
                Instant.now(),
                orgId,
                UUID.randomUUID(),
                ConfigurationEntityType.FEATURE_VERSION,
                "config-service",
                "corr-123",
                UUID.randomUUID(),
                "project-1",
                UUID.randomUUID(),
                "env-1",
                UUID.randomUUID(),
                "feature-1",
                UUID.randomUUID(),
                1,
                10L,
                true
        );

        when(kafkaTemplate.send(anyString(), anyString(), any(ConfigurationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null)); // We don't need a real result for this simple check

        publisher.publish(event);

        String expectedKey = orgId.toString() + ":project-1:env-1:feature-1";
        verify(kafkaTemplate).send("cloudscale.configuration-events", expectedKey, event);
    }
}
