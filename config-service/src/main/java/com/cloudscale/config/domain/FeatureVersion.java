package com.cloudscale.config.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record FeatureVersion(
        UUID id,
        UUID featureFlagId,
        int versionNumber,
        VersionStatus status,
        JsonNode value,
        UUID createdBy,
        Instant createdAt
) {
}
