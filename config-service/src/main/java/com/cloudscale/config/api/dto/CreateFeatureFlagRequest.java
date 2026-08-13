package com.cloudscale.config.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateFeatureFlagRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{0,63}$")
        String key,
        @NotBlank
        @Size(max = 120)
        String name,
        @Size(max = 500)
        String description,
        boolean enabled,
        @NotNull
        JsonNode value,
        List<@Valid TargetingRuleRequest> targetingRules
) {
}
