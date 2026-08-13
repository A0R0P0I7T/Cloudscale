package com.cloudscale.config.domain;

import java.time.Instant;
import java.util.UUID;

public record Project(
        UUID id,
        UUID organizationId,
        String key,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
