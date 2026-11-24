package com.cloudscale.config.events;

import com.cloudscale.contracts.configuration.ConfigurationEntityType;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.configuration.ConfigurationEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings({"unchecked", "rawtypes"})
class ConfigurationOutboxPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cloudscale_config")
            .withUsername("cloudscale")
            .withPassword("outbox-test-password");

    private JdbcTemplate jdbc;
    private ConfigurationOutboxRepository repository;
    private KafkaTemplate<String, ConfigurationEvent> kafkaTemplate;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("TRUNCATE TABLE configuration_event_outbox");
        objectMapper = new ObjectMapper().findAndRegisterModules();
        repository = new ConfigurationOutboxRepository(
                new NamedParameterJdbcTemplate(dataSource), objectMapper);
        kafkaTemplate = mock(KafkaTemplate.class);
    }

    @Test
    void failedKafkaSendPersistsRetryAndLaterAcknowledgementMarksTheSameRowPublished() {
        ConfigurationEvent event = event();
        repository.enqueue(event);
        CompletableFuture<SendResult<String, ConfigurationEvent>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any(ConfigurationEvent.class)))
                .thenReturn(failedSend, CompletableFuture.completedFuture(null));
        ConfigurationOutboxDispatcher dispatcher = new ConfigurationOutboxDispatcher(
                repository, kafkaTemplate, objectMapper, new SimpleMeterRegistry());

        dispatcher.dispatchPending();

        var afterFailure = jdbc.queryForMap("""
                SELECT attempts, next_attempt_at, published_at, last_error
                FROM configuration_event_outbox WHERE event_id = ?
                """, event.eventId());
        assertThat(afterFailure.get("attempts")).isEqualTo(1);
        assertThat(afterFailure.get("published_at")).isNull();
        assertThat(afterFailure.get("last_error")).isEqualTo("IllegalStateException");

        jdbc.update("UPDATE configuration_event_outbox SET next_attempt_at = NOW() WHERE event_id = ?",
                event.eventId());
        dispatcher.dispatchPending();

        var afterRetry = jdbc.queryForMap("""
                SELECT attempts, published_at, last_error
                FROM configuration_event_outbox WHERE event_id = ?
                """, event.eventId());
        assertThat(afterRetry.get("attempts")).isEqualTo(1);
        assertThat(afterRetry.get("published_at")).isNotNull();
        assertThat(afterRetry.get("last_error")).isNull();
    }

    private ConfigurationEvent event() {
        return new ConfigurationEvent(
                UUID.randomUUID(),
                ConfigurationEventType.CONFIGURATION_PUBLISHED,
                2,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ConfigurationEntityType.FEATURE_VERSION,
                "config-service",
                "trace-outbox-retry",
                UUID.randomUUID(),
                "web-store",
                UUID.randomUUID(),
                "production",
                UUID.randomUUID(),
                "checkout-v2",
                UUID.randomUUID(),
                1,
                4L,
                true);
    }
}
