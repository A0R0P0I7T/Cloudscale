package com.cloudscale.config.api.dto;

import com.cloudscale.config.domain.FeatureVersion;
import com.cloudscale.config.domain.TargetingRule;
import com.cloudscale.config.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeatureVersionResponse(
        UUID id,
        UUID featureFlagId,
        int versionNumber,
        VersionStatus status,
        JsonNode value,
        UUID createdBy,
        Instant createdAt,
        List<TargetingRuleResponse> targetingRules
) {
    public static FeatureVersionResponse from(FeatureVersion version, List<TargetingRule> rules) {
        return new FeatureVersionResponse(version.id(), version.featureFlagId(), version.versionNumber(),
                version.status(), version.value(), version.createdBy(), version.createdAt(),
                rules.stream().map(TargetingRuleResponse::from).toList());
    }
}
