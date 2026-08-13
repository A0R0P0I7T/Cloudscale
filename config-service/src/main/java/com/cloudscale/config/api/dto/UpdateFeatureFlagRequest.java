package com.cloudscale.config.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateFeatureFlagRequest(
        @Size(min = 1, max = 120)
        String name,
        @Size(max = 500)
        String description,
        Boolean enabled
) {
}
