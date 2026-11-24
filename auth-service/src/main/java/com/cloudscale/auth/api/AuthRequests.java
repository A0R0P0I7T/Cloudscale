package com.cloudscale.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String organizationName,
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{0,63}$") String organizationSlug,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 72) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._-]{0,63}$") String organizationSlug,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 72) String password
    ) {
    }
}
