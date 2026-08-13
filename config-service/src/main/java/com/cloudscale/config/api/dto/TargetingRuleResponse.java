package com.cloudscale.config.api.dto;

import com.cloudscale.config.domain.TargetingRule;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record TargetingRuleResponse(
        UUID id,
        int priority,
        JsonNode conditions,
        JsonNode value,
        Instant createdAt
) {
    public static TargetingRuleResponse from(TargetingRule rule) {
        return new TargetingRuleResponse(rule.id(), rule.priority(), rule.conditions(), rule.value(), rule.createdAt());
    }
}
