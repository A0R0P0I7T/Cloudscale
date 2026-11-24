package com.cloudscale.contracts.rollout;

import java.time.Instant;
import java.util.UUID;

public record RolloutEvent(
        UUID eventId,
        RolloutEventType eventType,
        int eventVersion,
        Instant occurredAt,
        UUID organizationId,
        UUID entityId,
        String entityType,
        String source,
        String correlationId,
        UUID rolloutId,
        UUID featureFlagId,
        UUID environmentId,
        Integer percentage,
        String status
) {
}
