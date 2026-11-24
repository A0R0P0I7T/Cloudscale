package com.cloudscale.auth.security;

import com.cloudscale.auth.persistence.AuthRepository.AuthAccount;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class JwtTokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttlSeconds;
    private final Clock clock;

    public JwtTokenService(String issuer, String secret, long ttlSeconds) {
        this(issuer, secret, ttlSeconds, Clock.systemUTC());
    }

    public JwtTokenService(String issuer, String secret, long ttlSeconds, Clock clock) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer is required");
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 UTF-8 bytes");
        }
        if (ttlSeconds < 60 || ttlSeconds > 86_400) {
            throw new IllegalArgumentException("JWT lifetime must be between 60 and 86400 seconds");
        }
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
    }

    public IssuedToken issue(AuthAccount account) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(account.userId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("organization_id", account.organizationId().toString())
                .claim("role", account.role())
                .claim("email", account.email())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), ttlSeconds);
    }

    public record IssuedToken(String value, long expiresIn) {
    }
}
