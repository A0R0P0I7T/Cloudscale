package com.cloudscale.rollout.domain;

import java.time.Instant;
import java.util.UUID;

public record Rollout(
        UUID id,
        UUID organizationId,
        UUID featureFlagId,
        UUID environmentId,
        RolloutStatus status,
        int currentPercentage,
        Instant createdAt,
        Instant updatedAt,
        Instant lastAutoPausedAt
) {
}
