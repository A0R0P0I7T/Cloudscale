package com.cloudscale.config.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record TargetingRule(
        UUID id,
        UUID featureVersionId,
        int priority,
        JsonNode conditions,
        JsonNode value,
        Instant createdAt
) {
}
