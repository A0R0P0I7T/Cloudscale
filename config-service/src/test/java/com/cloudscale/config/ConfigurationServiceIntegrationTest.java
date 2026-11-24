package com.cloudscale.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import com.cloudscale.config.events.ConfigurationEventPublisher;
import com.cloudscale.contracts.configuration.ConfigurationEntityType;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.configuration.ConfigurationEventType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigurationServiceIntegrationTest {
    private static final String JWT_ISSUER = "cloudscale-auth";
    private static final String JWT_SECRET = "configuration-test-secret-at-least-32-bytes";
    static {
        System.setProperty("api.version", "1.44");
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cloudscale_config")
            .withUsername("cloudscale")
            .withPassword("change-me");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("cloudscale.jwt.issuer", () -> JWT_ISSUER);
        registry.add("cloudscale.jwt.secret", () -> JWT_SECRET);
        registry.add("cloudscale.outbox.dispatcher.enabled", () -> false);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ConfigurationEventPublisher eventPublisher;

    @Test
    void migrationCreatesConfigurationTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('projects', 'environments', 'feature_flags', 'feature_versions',
                                     'targeting_rules', 'configuration_event_outbox')
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactly(
                "configuration_event_outbox", "environments", "feature_flags", "feature_versions",
                "projects", "targeting_rules");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint
                WHERE conname = 'feature_flags_published_version_fk'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void managesFeatureVersionsAndPublishesAtomically() {
        UUID organizationId = UUID.randomUUID();
        String projectKey = "project-" + UUID.randomUUID().toString().substring(0, 8);
        String environmentKey = "staging";
        String featureKey = "checkout-v2";

        ResponseEntity<Map<String, Object>> project = post(
                "/api/v1/organizations/" + organizationId + "/projects",
                Map.of("key", projectKey, "name", "Checkout", "description", "Checkout configuration"));
        assertThat(project.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> environment = post(
                "/api/v1/organizations/" + organizationId + "/projects/" + projectKey + "/environments",
                Map.of("key", environmentKey, "name", "Staging"));
        assertThat(environment.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> initialFeature = Map.of(
                "key", featureKey,
                "name", "Checkout V2",
                "enabled", false,
                "value", Map.of("variant", "control"),
                "targetingRules", List.of(Map.of(
                        "priority", 1,
                        "conditions", Map.of("region", "us-east"),
                        "value", Map.of("variant", "control"))));
        ResponseEntity<Map<String, Object>> feature = post(
                "/api/v1/organizations/" + organizationId + "/projects/" + projectKey
                        + "/environments/" + environmentKey + "/features", initialFeature);
        assertThat(feature.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String featureBase = "/api/v1/organizations/" + organizationId + "/projects/" + projectKey
                + "/environments/" + environmentKey + "/features/" + featureKey;
        ResponseEntity<Map<String, Object>> firstPublish = post(featureBase + "/versions/1/publish", null);
        assertThat(firstPublish.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPublish.getBody()).containsEntry("status", "PUBLISHED");

        ResponseEntity<Map<String, Object>> evaluationConfiguration = restTemplate.exchange(
                url(featureBase + "/evaluation-config"),
                HttpMethod.GET,
                new HttpEntity<>(headers(organizationId, "DEVELOPER")),
                new ParameterizedTypeReference<>() { });
        assertThat(evaluationConfiguration.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(evaluationConfiguration.getBody()).containsEntry("published", true);
        assertThat(evaluationConfiguration.getBody()).containsEntry("versionNumber", 1);

        ResponseEntity<Map<String, Object>> draft = post(featureBase + "/versions",
                Map.of("value", Map.of("variant", "treatment"), "targetingRules", List.of()));
        assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(draft.getBody()).containsEntry("versionNumber", 2);
        assertThat(draft.getBody()).containsEntry("status", "DRAFT");

        ResponseEntity<Map<String, Object>> secondPublish = post(featureBase + "/versions/2/publish", null);
        assertThat(secondPublish.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondPublish.getBody()).containsEntry("status", "PUBLISHED");

        ResponseEntity<List<Map<String, Object>>> versions = restTemplate.exchange(
                url(featureBase + "/versions"), HttpMethod.GET,
                new HttpEntity<>(headers(organizationId, "DEVELOPER")),
                new ParameterizedTypeReference<>() { });
        assertThat(versions.getBody()).extracting(version -> version.get("status"))
                .containsExactly("PUBLISHED", "ARCHIVED");

        ResponseEntity<Map<String, Object>> updatedFeature = patch(featureBase,
                Map.of("enabled", true, "name", "Checkout V2 Enabled"));
        assertThat(updatedFeature.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updatedFeature.getBody()).containsEntry("enabled", true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM configuration_event_outbox WHERE published_at IS NULL", Integer.class))
                .isEqualTo(3);

        ResponseEntity<Map<String, Object>> duplicateProject = post(
                "/api/v1/organizations/" + organizationId + "/projects",
                Map.of("key", projectKey, "name", "Duplicate"));
        assertThat(duplicateProject.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsDuplicateTargetingPriorities() {
        UUID organizationId = UUID.randomUUID();
        String projectKey = "project-" + UUID.randomUUID().toString().substring(0, 8);
        post("/api/v1/organizations/" + organizationId + "/projects",
                Map.of("key", projectKey, "name", "Project"));
        post("/api/v1/organizations/" + organizationId + "/projects/" + projectKey + "/environments",
                Map.of("key", "production", "name", "Production"));

        ResponseEntity<Map<String, Object>> response = post(
                "/api/v1/organizations/" + organizationId + "/projects/" + projectKey
                        + "/environments/production/features",
                Map.of(
                        "key", "duplicate-priority",
                        "name", "Duplicate Priority",
                        "enabled", false,
                        "value", true,
                        "targetingRules", List.of(
                                Map.of("priority", 1, "conditions", Map.of("tier", "free"), "value", false),
                                Map.of("priority", 1, "conditions", Map.of("tier", "paid"), "value", true))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "DUPLICATE_TARGETING_PRIORITY");
        assertThat(response.getBody()).containsKey("correlationId");
    }

    @Test
    void rejectsUnauthenticatedCrossOrganizationAndViewerWrites() {
        UUID organizationId = UUID.randomUUID();
        String projectsPath = "/api/v1/organizations/" + organizationId + "/projects";
        ResponseEntity<Map<String, Object>> unauthenticated = restTemplate.exchange(
                url(projectsPath), HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() { });
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        UUID differentOrganization = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> crossOrganization = restTemplate.exchange(
                url(projectsPath), HttpMethod.GET, new HttpEntity<>(headers(differentOrganization, "ADMIN")),
                new ParameterizedTypeReference<>() { });
        assertThat(crossOrganization.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, Object>> viewerWrite = restTemplate.exchange(
                url(projectsPath), HttpMethod.POST,
                new HttpEntity<>(Map.of("key", "blocked", "name", "Blocked"), headers(organizationId, "VIEWER")),
                new ParameterizedTypeReference<>() { });
        assertThat(viewerWrite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void outboxEntryRollsBackWithConfigurationTransaction() {
        UUID organizationId = UUID.randomUUID();
        ConfigurationEvent event = new ConfigurationEvent(
                UUID.randomUUID(), ConfigurationEventType.CONFIGURATION_ENABLED, 2, Instant.now(),
                organizationId, UUID.randomUUID(), ConfigurationEntityType.FEATURE_FLAG,
                "config-service", "rollback-test", UUID.randomUUID(), "project", UUID.randomUUID(),
                "staging", UUID.randomUUID(), "checkout", null, null, 1L, true);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            eventPublisher.publish(event);
            throw new IllegalStateException("force transaction rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM configuration_event_outbox WHERE event_id = ?",
                Integer.class, event.eventId())).isZero();
    }

    private ResponseEntity<Map<String, Object>> post(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body, headers(organizationIdFromPath(path), "DEVELOPER")),
                new ParameterizedTypeReference<>() { });
    }

    private ResponseEntity<Map<String, Object>> patch(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.PATCH,
                new HttpEntity<>(body, headers(organizationIdFromPath(path), "DEVELOPER")),
                new ParameterizedTypeReference<>() { });
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders headers(UUID organizationId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        headers.setBearerAuth(token(organizationId, role));
        headers.add("X-Correlation-Id", UUID.randomUUID().toString());
        return headers;
    }

    private UUID organizationIdFromPath(String path) {
        return UUID.fromString(path.split("/")[4]);
    }

    private String token(UUID organizationId, String role) {
        byte[] secret = JWT_SECRET.getBytes(StandardCharsets.UTF_8);
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(
                new SecretKeySpec(secret, "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JWT_ISSUER)
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
}
