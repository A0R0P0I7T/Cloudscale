package com.cloudscale.config.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateFeatureVersionRequest(
        @NotNull
        JsonNode value,
        List<@Valid TargetingRuleRequest> targetingRules
) {
}
