package com.cloudscale.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigurationServiceIntegrationTest {
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
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesConfigurationTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('projects', 'environments', 'feature_flags', 'feature_versions', 'targeting_rules')
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactly(
                "environments", "feature_flags", "feature_versions", "projects", "targeting_rules");
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
                url(featureBase + "/evaluation-config"), HttpMethod.GET, HttpEntity.EMPTY,
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
                url(featureBase + "/versions"), HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() { });
        assertThat(versions.getBody()).extracting(version -> version.get("status"))
                .containsExactly("PUBLISHED", "ARCHIVED");

        ResponseEntity<Map<String, Object>> updatedFeature = patch(featureBase,
                Map.of("enabled", true, "name", "Checkout V2 Enabled"));
        assertThat(updatedFeature.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updatedFeature.getBody()).containsEntry("enabled", true);

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

    private ResponseEntity<Map<String, Object>> post(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), new ParameterizedTypeReference<>() { });
    }

    private ResponseEntity<Map<String, Object>> patch(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.PATCH,
                new HttpEntity<>(body, jsonHeaders()), new ParameterizedTypeReference<>() { });
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }
}
