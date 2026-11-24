package com.cloudscale.auth;

import com.cloudscale.auth.persistence.AuthRepository;
import com.cloudscale.auth.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {
    private static final String SECRET = "local-test-secret-with-at-least-32-bytes";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void rejectsMissingOrWeakJwtSecret() {
        assertThatThrownBy(() -> new JwtTokenService("cloudscale-auth", "", 900))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtTokenService("cloudscale-auth", "too-short", 900))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenContainsSignedMembershipClaimsAndConfiguredExpiry() {
        JwtTokenService service = new JwtTokenService(
                "cloudscale-auth", SECRET, 900, Clock.fixed(NOW, ZoneOffset.UTC));
        AuthRepository.AuthAccount account = new AuthRepository.AuthAccount(
                UUID.fromString("5dc89653-2f06-4ab1-9613-f74c4febf743"),
                UUID.fromString("d2df2e2a-36e2-4ecf-a90a-d72ab5e6fb8c"),
                "cloudscale-demo", "CloudScale Demo", "ada@example.com", "unused-hash", "ADMIN");

        JwtTokenService.IssuedToken issued = service.issue(account);
        NimbusJwtDecoder decoder = decoder(SECRET);
        Jwt decoded = decoder.decode(issued.value());

        assertThat(decoded.getSubject()).isEqualTo(account.userId().toString());
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("cloudscale-auth");
        assertThat(decoded.getClaimAsString("organization_id")).isEqualTo(account.organizationId().toString());
        assertThat(decoded.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(decoded.getClaimAsString("jti")).isNotBlank();
        assertThat(decoded.getIssuedAt()).isEqualTo(NOW);
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(issued.expiresIn()).isEqualTo(900);
    }

    @Test
    void rejectsTokenWhenVerifierIssuerDoesNotMatch() {
        JwtTokenService service = new JwtTokenService(
                "cloudscale-auth", SECRET, 900, Clock.fixed(NOW, ZoneOffset.UTC));
        AuthRepository.AuthAccount account = new AuthRepository.AuthAccount(
                UUID.randomUUID(), UUID.randomUUID(), "cloudscale-demo", "CloudScale Demo",
                "ada@example.com", "unused-hash", "VIEWER");
        NimbusJwtDecoder decoder = decoder(SECRET);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("another-issuer"));

        assertThatThrownBy(() -> decoder.decode(service.issue(account).value()))
                .isInstanceOf(JwtException.class);
    }

    private NimbusJwtDecoder decoder(String secret) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(
                        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("cloudscale-auth"));
        return decoder;
    }
}
