package com.cloudscale.evaluation.events;

import com.cloudscale.contracts.configuration.ConfigurationEntityType;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.configuration.ConfigurationEventType;
import com.cloudscale.evaluation.cache.EvaluationCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationEventHandlerTest {

    private EvaluationCache evaluationCache;
    private ConfigurationEventHandler handler;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        evaluationCache = mock(EvaluationCache.class);
        meterRegistry = new SimpleMeterRegistry();
        handler = new ConfigurationEventHandler(evaluationCache, meterRegistry);
    }

    @Test
    void handle_invalidatesCacheWhenValidEvent() {
        ConfigurationEvent event = new ConfigurationEvent(
                UUID.randomUUID(),
                ConfigurationEventType.CONFIGURATION_PUBLISHED,
                2,
                Instant.now(),
                UUID.randomUUID(),
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

        when(evaluationCache.invalidate(anyString(), anyLong())).thenReturn(true);

        handler.handle(event);

        String expectedKey = "cloudscale:evaluation-config:" + event.organizationId() + ":project-1:env-1:feature-1";
        verify(evaluationCache).invalidate(expectedKey, 10L);
    }

    @Test
    void handle_ignoresEventWithoutRevision() {
        ConfigurationEvent event = new ConfigurationEvent(
                UUID.randomUUID(),
                ConfigurationEventType.CONFIGURATION_PUBLISHED,
                2,
                Instant.now(),
                UUID.randomUUID(),
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
                null,
                true
        );

        handler.handle(event);

        verify(evaluationCache, never()).invalidate(anyString(), anyLong());
    }

    @Test
    void cacheFailureIsCountedAndRethrownForKafkaRetry() {
        ConfigurationEvent event = new ConfigurationEvent(
                UUID.randomUUID(),
                ConfigurationEventType.CONFIGURATION_PUBLISHED,
                2,
                Instant.now(),
                UUID.randomUUID(),
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
                true);
        when(evaluationCache.invalidate(anyString(), anyLong()))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(meterRegistry.get("cloudscale.evaluation.configuration.events.errors")
                .tag("eventType", "all").counter().count()).isEqualTo(1.0);
    }
}
