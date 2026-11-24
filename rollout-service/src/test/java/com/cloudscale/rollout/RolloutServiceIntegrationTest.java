package com.cloudscale.rollout;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.sql.Timestamp;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RolloutServiceIntegrationTest {
    private static final String JWT_SECRET = "rollout-test-secret-at-least-32-bytes";

    static {
        System.setProperty("api.version", "1.44");
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cloudscale_rollout")
            .withUsername("cloudscale")
            .withPassword("change-me");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("cloudscale.jwt.issuer", () -> "cloudscale-auth");
        registry.add("cloudscale.jwt.secret", () -> JWT_SECRET);
        registry.add("cloudscale.outbox.dispatcher.enabled", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void migrationCreatesRolloutTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('rollouts', 'rollout_stages', 'health_policies',
                                     'health_observations', 'rollout_event_outbox')
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactly(
                "health_observations", "health_policies", "rollout_event_outbox", "rollout_stages", "rollouts");
    }

    @Test
    void createsAndControlsRolloutWithDeterministicAssignmentAndSafeHealthPause() {
        UUID organizationId = UUID.randomUUID();
        UUID featureFlagId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String base = "/api/v1/organizations/" + organizationId + "/rollouts";
        Map<String, Object> request = Map.of(
                "featureFlagId", featureFlagId,
                "environmentId", environmentId,
                "stages", List.of(
                        Map.of("targetPercentage", 10, "durationMinutes", 1),
                        Map.of("targetPercentage", 100, "durationMinutes", 1)),
                "healthPolicy", Map.of(
                        "metric", "ERROR_RATE",
                        "threshold", 0.05,
                        "minimumRequestCount", 100,
                        "evaluationWindowSeconds", 300,
                        "cooldownSeconds", 300));

        ResponseEntity<Map<String, Object>> created = post(base, request, organizationId, "DEVELOPER");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("status", "DRAFT");
        String rolloutId = created.getBody().get("id").toString();

        ResponseEntity<Map<String, Object>> started = post(
                base + "/" + rolloutId + "/start", null, organizationId, "DEVELOPER");
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(started.getBody()).containsEntry("status", "IN_PROGRESS").containsEntry("currentPercentage", 10);

        ResponseEntity<Map<String, Object>> competingDraft = post(base, request, organizationId, "DEVELOPER");
        String competingId = competingDraft.getBody().get("id").toString();
        ResponseEntity<Map<String, Object>> competingStart = post(
                base + "/" + competingId + "/start", null, organizationId, "DEVELOPER");
        assertThat(competingStart.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> assignmentRequest = Map.of(
                "featureFlagId", featureFlagId,
                "environmentId", environmentId,
                "userId", "stable-user-1");
        ResponseEntity<Map<String, Object>> firstAssignment = post(
                base + "/assignment", assignmentRequest, organizationId, "VIEWER");
        ResponseEntity<Map<String, Object>> repeatedAssignment = post(
                base + "/assignment", assignmentRequest, organizationId, "VIEWER");
        assertThat(firstAssignment.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repeatedAssignment.getBody().get("bucket"))
                .isEqualTo(firstAssignment.getBody().get("bucket"));

        ResponseEntity<Map<String, Object>> insufficientSample = post(
                base + "/" + rolloutId + "/observations",
                Map.of("requestCount", 99, "errorRate", 0.9, "latencyMs", 100, "observedAt", Instant.now()),
                organizationId, "DEVELOPER");
        assertThat(insufficientSample.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(insufficientSample.getBody()).containsEntry("automaticallyPaused", false);
        assertThat(insufficientSample.getBody()).containsEntry("rolloutStatus", "IN_PROGRESS");

        ResponseEntity<Map<String, Object>> unhealthySample = post(
                base + "/" + rolloutId + "/observations",
                Map.of("requestCount", 100, "errorRate", 0.10, "latencyMs", 100, "observedAt", Instant.now()),
                organizationId, "DEVELOPER");
        assertThat(unhealthySample.getBody()).containsEntry("automaticallyPaused", true);
        assertThat(unhealthySample.getBody()).containsEntry("rolloutStatus", "PAUSED");

        ResponseEntity<Map<String, Object>> earlyResume = post(
                base + "/" + rolloutId + "/resume", null, organizationId, "DEVELOPER");
        assertThat(earlyResume.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map<String, Object>> rollback = post(
                base + "/" + rolloutId + "/rollback", null, organizationId, "DEVELOPER");
        assertThat(rollback.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rollback.getBody()).containsEntry("status", "ROLLED_BACK").containsEntry("currentPercentage", 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rollout_event_outbox WHERE published_at IS NULL", Integer.class))
                .isGreaterThanOrEqualTo(5);
    }

    @Test
    void deniesUnauthenticatedAndCrossOrganizationRolloutRequests() {
        UUID organizationId = UUID.randomUUID();
        String path = "/api/v1/organizations/" + organizationId + "/rollouts";
        ResponseEntity<Map<String, Object>> unauthenticated = restTemplate.exchange(
                url(path), HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() { });
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map<String, Object>> crossOrganization = restTemplate.exchange(
                url(path), HttpMethod.GET, new HttpEntity<>(headers(UUID.randomUUID(), "ADMIN")),
                new ParameterizedTypeReference<>() { });
        assertThat(crossOrganization.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsMissingStagesAsBadRequest() {
        UUID organizationId = UUID.randomUUID();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("featureFlagId", UUID.randomUUID());
        request.put("environmentId", UUID.randomUUID());
        request.put("stages", null);

        ResponseEntity<Map<String, Object>> response = post(
                "/api/v1/organizations/" + organizationId + "/rollouts",
                request,
                organizationId,
                "DEVELOPER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void newestRolledBackRolloutSuppressesOlderCompletedExposure() {
        UUID organizationId = UUID.randomUUID();
        UUID featureFlagId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        Instant older = Instant.now().minusSeconds(60);
        UUID olderCompletedId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO rollouts
                    (id, organization_id, feature_flag_id, environment_id, status, current_percentage,
                     created_at, updated_at, last_auto_paused_at)
                VALUES (?, ?, ?, ?, 'COMPLETED', 100, ?, ?, NULL)
                """, olderCompletedId, organizationId, featureFlagId, environmentId,
                Timestamp.from(older), Timestamp.from(older));

        String base = "/api/v1/organizations/" + organizationId + "/rollouts";
        Map<String, Object> request = Map.of(
                "featureFlagId", featureFlagId,
                "environmentId", environmentId,
                "stages", List.of(Map.of("targetPercentage", 100, "durationMinutes", 1)));
        ResponseEntity<Map<String, Object>> created = post(base, request, organizationId, "DEVELOPER");
        String currentId = created.getBody().get("id").toString();
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(base + "/" + currentId + "/start", null, organizationId, "DEVELOPER").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<Map<String, Object>> rolledBack = post(
                base + "/" + currentId + "/rollback", null, organizationId, "DEVELOPER");
        assertThat(rolledBack.getBody()).containsEntry("status", "ROLLED_BACK").containsEntry("currentPercentage", 0);

        ResponseEntity<Map<String, Object>> assignment = post(
                base + "/assignment",
                Map.of("featureFlagId", featureFlagId, "environmentId", environmentId, "userId", "stable-user"),
                organizationId,
                "VIEWER");

        assertThat(assignment.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assignment.getBody())
                .containsEntry("rolloutId", currentId)
                .containsEntry("status", "ROLLED_BACK")
                .containsEntry("percentage", 0)
                .containsEntry("assigned", false);
        assertThat(olderCompletedId).isNotEqualTo(currentId);
    }

    private ResponseEntity<Map<String, Object>> post(
            String path,
            Object body,
            UUID tokenOrganizationId,
            String role
    ) {
        return restTemplate.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body, headers(tokenOrganizationId, role)),
                new ParameterizedTypeReference<>() { });
    }

    private HttpHeaders headers(UUID organizationId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(organizationId, role));
        headers.add("X-Correlation-Id", UUID.randomUUID().toString());
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }

    private String token(UUID organizationId, String role) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(
                new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cloudscale-auth")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .id(UUID.randomUUID().toString())
                .claim("organization_id", organizationId.toString())
                .claim("role", role)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
