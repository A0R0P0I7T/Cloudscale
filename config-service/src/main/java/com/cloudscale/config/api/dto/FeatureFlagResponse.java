package com.cloudscale.config.api.dto;

import com.cloudscale.config.domain.FeatureFlag;

import java.time.Instant;
import java.util.UUID;

public record FeatureFlagResponse(
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
    public static FeatureFlagResponse from(FeatureFlag featureFlag) {
        return new FeatureFlagResponse(featureFlag.id(), featureFlag.environmentId(), featureFlag.key(),
                featureFlag.name(), featureFlag.description(), featureFlag.enabled(),
                featureFlag.publishedVersionId(), featureFlag.revision(), featureFlag.createdAt(), featureFlag.updatedAt());
    }
}
