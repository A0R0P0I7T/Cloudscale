package com.cloudscale.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewaySecurityConfigurationTest {
    private static final String SECRET = "gateway-test-secret-with-at-least-32-bytes";
    private final GatewaySecurityConfiguration configuration = new GatewaySecurityConfiguration();

    @Test
    void rejectsMissingOrWeakJwtConfiguration() {
        assertThatThrownBy(() -> configuration.reactiveJwtDecoder("cloudscale-auth", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.reactiveJwtDecoder("cloudscale-auth", "short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.reactiveJwtDecoder("", SECRET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configuredDecoderRejectsAValidSignatureWithTheWrongIssuer() {
        ReactiveJwtDecoder decoder = configuration.reactiveJwtDecoder("cloudscale-auth", SECRET);

        assertThatThrownBy(() -> decoder.decode(token("another-issuer")).block())
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
