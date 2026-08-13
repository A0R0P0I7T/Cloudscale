package com.cloudscale.evaluation.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;
import java.time.Instant;

public record TargetingRuleConfiguration(
        UUID id,
        int priority,
        JsonNode conditions,
        JsonNode value,
        Instant createdAt
) {
}
