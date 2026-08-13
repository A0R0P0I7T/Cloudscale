package com.cloudscale.config.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TargetingRuleRequest(
        @Positive
        int priority,
        @NotNull
        JsonNode conditions,
        @NotNull
        JsonNode value
) {
}
