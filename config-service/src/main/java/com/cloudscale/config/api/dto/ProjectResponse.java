package com.cloudscale.config.api.dto;

import com.cloudscale.config.domain.Project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID organizationId,
        String key,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.id(), project.organizationId(), project.key(), project.name(),
                project.description(), project.createdAt(), project.updatedAt());
    }
}
