package com.cloudscale.audit.application;

import com.cloudscale.audit.persistence.AuditEventRepository;
import com.cloudscale.contracts.configuration.ConfigurationEntityType;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.configuration.ConfigurationEventType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {
    @Test
    void reportsDuplicateEventsWithoutCountingThemAsNew() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditService auditService = new AuditService(repository, registry);
        ConfigurationEvent event = new ConfigurationEvent(
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
                "staging",
                UUID.randomUUID(),
                "checkout",
                UUID.randomUUID(),
                1,
                7L,
                true);
        when(repository.recordConfigurationEvent(event)).thenReturn(true, false);

        assertThat(auditService.record(event)).isTrue();
        assertThat(auditService.record(event)).isFalse();
        assertThat(registry.get("cloudscale.audit.events.processed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("cloudscale.audit.events.duplicates").counter().count()).isEqualTo(1.0);
        verify(repository, org.mockito.Mockito.times(2)).recordConfigurationEvent(event);
    }
}
