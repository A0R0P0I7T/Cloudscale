package com.cloudscale.config.api.dto;

import com.cloudscale.config.domain.Environment;

import java.time.Instant;
import java.util.UUID;

public record EnvironmentResponse(
        UUID id,
        UUID projectId,
        String key,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static EnvironmentResponse from(Environment environment) {
        return new EnvironmentResponse(environment.id(), environment.projectId(), environment.key(),
                environment.name(), environment.description(), environment.createdAt(), environment.updatedAt());
    }
}
