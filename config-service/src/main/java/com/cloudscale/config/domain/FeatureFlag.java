package com.cloudscale.config.domain;

import java.time.Instant;
import java.util.UUID;

public record FeatureFlag(
        UUID id,
        UUID environmentId,
        String key,
        String name,
        String description,
        boolean enabled,
        UUID publishedVersionId,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
}
