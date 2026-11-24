package com.cloudscale.auth.api;

import com.cloudscale.auth.persistence.AuthRepository.AuthAccount;
import com.cloudscale.auth.security.JwtTokenService.IssuedToken;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        UUID organizationId,
        String organizationSlug,
        String organizationName,
        String role,
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static AuthResponse from(AuthAccount account, IssuedToken token) {
        return new AuthResponse(account.userId(), account.organizationId(), account.organizationSlug(),
                account.organizationName(), account.role(), token.value(), "Bearer", token.expiresIn());
    }
}
