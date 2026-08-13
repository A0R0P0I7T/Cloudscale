package com.cloudscale.config.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{0,63}$")
        String key,
        @NotBlank
        @Size(max = 120)
        String name,
        @Size(max = 500)
        String description
) {
}
