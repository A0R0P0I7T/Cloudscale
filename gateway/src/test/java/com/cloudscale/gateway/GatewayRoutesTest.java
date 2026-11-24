package com.cloudscale.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.port=0",
        "cloudscale.jwt.issuer=cloudscale-auth",
        "cloudscale.jwt.secret=route-test-secret-at-least-32-bytes"
})
@AutoConfigureWebTestClient
class GatewayRoutesTest {
    private static final String JWT_SECRET = "route-test-secret-at-least-32-bytes";

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void routesPublicApiPathsToOwningServices() {
        Map<String, URI> destinations = routeLocator.getRoutes().collectList().block().stream()
                .collect(Collectors.toMap(Route::getId, Route::getUri));

        assertThat(destinations)
                .containsEntry("auth-service", URI.create("http://localhost:8081"))
                .containsEntry("evaluation-service", URI.create("http://localhost:8084"))
                .containsEntry("rollout-service", URI.create("http://localhost:8083"))
                .containsEntry("audit-service", URI.create("http://localhost:8085"))
                .containsEntry("config-service", URI.create("http://localhost:8082"));
    }

    @Test
    void rejectsMissingAndInvalidTokensForOrganizationApisWithCorrelationIds() {
        String path = "/api/v1/organizations/" + java.util.UUID.randomUUID() + "/projects";

        webTestClient.get().uri(path).exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches("X-Correlation-Id", "[0-9a-f-]{36}");

        webTestClient.get().uri(path).header("Authorization", "Bearer invalid.token").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches("X-Correlation-Id", "[0-9a-f-]{36}");
    }

    @Test
    void acceptsValidSignedJwtBeforeRouteResolution() {
        String token = token("cloudscale-auth");

        webTestClient.get().uri("/unmapped-protected")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueMatches("X-Correlation-Id", "[0-9a-f-]{36}");
    }

    @Test
    void rejectsSignedJwtFromAnotherIssuer() {
        webTestClient.get().uri("/unmapped-protected")
                .header("Authorization", "Bearer " + token("another-issuer"))
                .exchange()
                .expectStatus().isUnauthorized();
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
                .claim("role", "DEVELOPER")
                .build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(
                new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
