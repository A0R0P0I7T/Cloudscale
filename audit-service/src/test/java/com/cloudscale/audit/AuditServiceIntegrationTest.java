package com.cloudscale.audit;

import com.cloudscale.audit.api.AuditEventResponse;
import com.cloudscale.audit.application.AuditService;
import com.cloudscale.contracts.configuration.ConfigurationEntityType;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.configuration.ConfigurationEventType;
import org.springframework.kafka.core.KafkaTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditServiceIntegrationTest {
    static {
        System.setProperty("api.version", "1.44");
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cloudscale_audit")
            .withUsername("cloudscale")
            .withPassword("change-me");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.0");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.producer.key-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer",
                () -> "org.springframework.kafka.support.serializer.JsonSerializer");
        registry.add("cloudscale.jwt.issuer", () -> "cloudscale-auth");
        registry.add("cloudscale.jwt.secret", () -> "audit-test-secret-at-least-32-bytes");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditService auditService;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Test
    void migrationCreatesAuditTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('audit_events', 'processed_events')
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactly("audit_events", "processed_events");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'audit_events_org_time_idx'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void persistsAuditEventsIdempotentlyAndScopesQueriesByOrganization() {
        UUID organizationId = UUID.randomUUID();
        ConfigurationEvent event = event(organizationId);
        ConfigurationEvent otherOrganizationEvent = event(UUID.randomUUID());

        assertThat(auditService.record(event)).isTrue();
        assertThat(auditService.record(event)).isFalse();
        assertThat(auditService.record(otherOrganizationEvent)).isTrue();

        List<AuditEventResponse> events = auditService.list(organizationId, 0, 50);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().eventId()).isEqualTo(event.eventId());
        assertThat(events.getFirst().organizationId()).isEqualTo(organizationId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Integer.class, event.eventId()))
                .isEqualTo(1);
    }

    @Test
    void consumesKafkaConfigurationEventsIdempotently() throws Exception {
        ConfigurationEvent event = event(UUID.randomUUID());
        kafkaTemplate.send("cloudscale.configuration-events", event.eventId().toString(), event)
                .get(10, TimeUnit.SECONDS);
        kafkaTemplate.send("cloudscale.configuration-events", event.eventId().toString(), event)
                .get(10, TimeUnit.SECONDS);

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_events WHERE event_id = ?",
                        Integer.class, event.eventId())).isEqualTo(1));
    }

    private ConfigurationEvent event(UUID organizationId) {
        return new ConfigurationEvent(
                UUID.randomUUID(),
                ConfigurationEventType.CONFIGURATION_PUBLISHED,
                2,
                Instant.now(),
                organizationId,
                UUID.randomUUID(),
                ConfigurationEntityType.FEATURE_VERSION,
                "config-service",
                "audit-test",
                UUID.randomUUID(),
                "project",
                UUID.randomUUID(),
                "staging",
                UUID.randomUUID(),
                "checkout",
                UUID.randomUUID(),
                1,
                2L,
                true);
    }
}
