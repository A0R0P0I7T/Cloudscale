package com.cloudscale.config.domain;

import java.time.Instant;
import java.util.UUID;

public record Environment(
        UUID id,
        UUID projectId,
        String key,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
