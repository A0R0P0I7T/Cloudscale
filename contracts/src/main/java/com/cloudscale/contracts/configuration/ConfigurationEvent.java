package com.cloudscale.contracts.configuration;

import java.time.Instant;
import java.util.UUID;

public record ConfigurationEvent(
        UUID eventId,
        ConfigurationEventType eventType,
        int eventVersion,
        Instant occurredAt,
        UUID organizationId,
        UUID entityId,
        ConfigurationEntityType entityType,
        String source,
        String correlationId,
        UUID projectId,
        String projectKey,
        UUID environmentId,
        String environmentKey,
        UUID featureFlagId,
        String featureKey,
        UUID featureVersionId,
        Integer versionNumber,
        Long revision,
        Boolean enabled
) {
}
