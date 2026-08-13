package com.cloudscale.evaluation.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeatureEvaluationConfiguration(
        UUID featureId,
        UUID environmentId,
        String featureKey,
        boolean enabled,
        boolean published,
        UUID publishedVersionId,
        Integer versionNumber,
        JsonNode value,
        List<TargetingRuleConfiguration> targetingRules,
        long revision,
        Instant updatedAt
) {
    public List<TargetingRuleConfiguration> safeTargetingRules() {
        return targetingRules == null ? List.of() : targetingRules;
    }
}
