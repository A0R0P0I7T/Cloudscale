package com.cloudscale.audit.events;

import com.cloudscale.audit.application.AuditService;
import com.cloudscale.contracts.configuration.ConfigurationEntityType;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.configuration.ConfigurationEventType;
import com.cloudscale.contracts.rollout.RolloutEvent;
import com.cloudscale.contracts.rollout.RolloutEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditEventListenerTest {
    @Test
    void delegatesBothDomainTopicsToIdempotentAuditService() {
        AuditService auditService = mock(AuditService.class);
        AuditEventListener listener = new AuditEventListener(auditService);
        UUID organizationId = UUID.randomUUID();
        ConfigurationEvent configurationEvent = new ConfigurationEvent(
                UUID.randomUUID(), ConfigurationEventType.CONFIGURATION_PUBLISHED, 2, Instant.now(),
                organizationId, UUID.randomUUID(), ConfigurationEntityType.FEATURE_VERSION,
                "config-service", "trace-1", UUID.randomUUID(), "project", UUID.randomUUID(),
                "staging", UUID.randomUUID(), "checkout", UUID.randomUUID(), 1, 3L, true);
        RolloutEvent rolloutEvent = new RolloutEvent(
                UUID.randomUUID(), RolloutEventType.ROLLOUT_STARTED, 1, Instant.now(),
                organizationId, UUID.randomUUID(), "ROLLOUT", "rollout-service", "trace-2",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 25, "IN_PROGRESS");

        listener.onConfigurationEvent(configurationEvent);
        listener.onRolloutEvent(rolloutEvent);

        verify(auditService).record(configurationEvent);
        verify(auditService).record(rolloutEvent);
    }
}
