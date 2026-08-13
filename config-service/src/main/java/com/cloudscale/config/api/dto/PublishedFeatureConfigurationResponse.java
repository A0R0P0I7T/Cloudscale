package com.cloudscale.config.api.dto;

import com.cloudscale.config.domain.FeatureFlag;
import com.cloudscale.config.domain.FeatureVersion;
import com.cloudscale.config.domain.TargetingRule;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublishedFeatureConfigurationResponse(
        UUID featureId,
        UUID environmentId,
        String featureKey,
        boolean enabled,
        boolean published,
        UUID publishedVersionId,
        Integer versionNumber,
        JsonNode value,
        List<TargetingRuleResponse> targetingRules,
        long revision,
        Instant updatedAt
) {
    public static PublishedFeatureConfigurationResponse from(
            FeatureFlag featureFlag,
            FeatureVersion version,
            List<TargetingRule> rules
    ) {
        return new PublishedFeatureConfigurationResponse(
                featureFlag.id(),
                featureFlag.environmentId(),
                featureFlag.key(),
                featureFlag.enabled(),
                version != null,
                version == null ? null : version.id(),
                version == null ? null : version.versionNumber(),
                version == null ? null : version.value(),
                rules.stream().map(TargetingRuleResponse::from).toList(),
                featureFlag.revision(),
                featureFlag.updatedAt());
    }
}
