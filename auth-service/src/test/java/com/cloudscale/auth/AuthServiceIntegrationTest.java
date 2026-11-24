package com.cloudscale.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
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

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthServiceIntegrationTest {
    static {
        System.setProperty("api.version", "1.44");
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cloudscale_auth")
            .withUsername("cloudscale")
            .withPassword("change-me");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("cloudscale.jwt.issuer", () -> "cloudscale-auth");
        registry.add("cloudscale.jwt.secret", () -> "integration-test-secret-at-least-32-bytes");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void migrationCreatesAuthTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('organizations', 'users', 'memberships')
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactly("memberships", "organizations", "users");
    }

    @Test
    void registersWithHashedPasswordAndLogsInThroughOrganizationMembership() {
        String email = "user-" + System.nanoTime() + "@example.com";
        String slug = "org-" + UUID.randomUUID().toString().substring(0, 8);
        String password = "a-long-local-password";
        Map<String, Object> registration = Map.of(
                "organizationName", "CloudScale Demo",
                "organizationSlug", slug,
                "fullName", "Ada Lovelace",
                "email", email,
                "password", password);

        ResponseEntity<Map<String, Object>> created = post("/api/v1/auth/register", registration);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("organizationSlug", slug);
        assertThat(created.getBody()).containsEntry("role", "ADMIN");
        assertThat(created.getBody().get("accessToken").toString()).startsWith("ey");
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(passwordHash).isNotEqualTo(password).startsWith("$2");

        ResponseEntity<Map<String, Object>> login = post("/api/v1/auth/login",
                Map.of("organizationSlug", slug, "email", email, "password", password));

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).containsEntry("organizationSlug", slug);
        assertThat(login.getBody()).containsEntry("role", "ADMIN");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<Map<String, Object>> post(String path, Object body) {
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body),
                new ParameterizedTypeReference<>() { });
    }
}
