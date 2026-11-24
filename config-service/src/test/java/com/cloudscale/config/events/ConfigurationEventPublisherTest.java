package com.cloudscale.config.events;

import com.cloudscale.contracts.configuration.ConfigurationEntityType;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.configuration.ConfigurationEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ConfigurationEventPublisherTest {
    private ConfigurationOutboxRepository outboxRepository;
    private ConfigurationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(ConfigurationOutboxRepository.class);
        publisher = new ConfigurationEventPublisher(outboxRepository);
    }

    @Test
    void publishPersistsEventInTransactionalOutbox() {
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

        publisher.publish(event);

        verify(outboxRepository).enqueue(event);
        verifyNoMoreInteractions(outboxRepository);
    }
}
