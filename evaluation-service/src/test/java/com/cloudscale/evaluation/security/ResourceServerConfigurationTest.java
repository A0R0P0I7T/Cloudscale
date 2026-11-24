package com.cloudscale.evaluation.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceServerConfigurationTest {
    private static final String SECRET = "resource-server-test-secret-at-least-32-bytes";
    private final ResourceServerConfiguration configuration = new ResourceServerConfiguration();

    @Test
    void rejectsMissingOrWeakSecrets() {
        assertThatThrownBy(() -> configuration.jwtDecoder("cloudscale-auth", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.jwtDecoder("cloudscale-auth", "short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.jwtDecoder("", SECRET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configuredDecoderRejectsAValidSignatureWithTheWrongIssuer() {
        JwtDecoder decoder = configuration.jwtDecoder("cloudscale-auth", SECRET);

        assertThatThrownBy(() -> decoder.decode(token("another-issuer")))
                .isInstanceOf(JwtException.class);
    }

    private String token(String issuer) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .id(UUID.randomUUID().toString())
                .claim("organization_id", UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
