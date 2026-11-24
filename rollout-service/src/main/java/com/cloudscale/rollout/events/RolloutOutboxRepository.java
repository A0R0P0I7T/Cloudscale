package com.cloudscale.rollout.events;

import com.cloudscale.contracts.rollout.RolloutEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class RolloutOutboxRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RolloutOutboxRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void enqueue(RolloutEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Rollout event could not be serialized", exception);
        }
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO rollout_event_outbox
                    (event_id, message_key, payload, created_at, attempts, next_attempt_at)
                VALUES (:eventId, :messageKey, CAST(:payload AS jsonb), :createdAt, 0, :nextAttemptAt)
                """, new MapSqlParameterSource()
                .addValue("eventId", event.eventId())
                .addValue("messageKey", event.organizationId() + ":" + event.rolloutId())
                .addValue("payload", payload)
                .addValue("createdAt", Timestamp.from(now))
                .addValue("nextAttemptAt", Timestamp.from(now)));
    }

    public List<PendingEvent> lockNextBatch(int limit, Instant now) {
        return jdbc.query("""
                SELECT event_id, message_key, payload::text AS payload, attempts
                FROM rollout_event_outbox
                WHERE published_at IS NULL AND next_attempt_at <= :now
                ORDER BY created_at, event_id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """, new MapSqlParameterSource()
                        .addValue("now", Timestamp.from(now))
                        .addValue("limit", limit),
                (rs, rowNum) -> new PendingEvent(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("message_key"),
                        rs.getString("payload"),
                        rs.getInt("attempts")));
    }

    public void markPublished(UUID eventId, Instant publishedAt) {
        jdbc.update("""
                UPDATE rollout_event_outbox
                SET published_at = :publishedAt, last_error = NULL
                WHERE event_id = :eventId AND published_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("publishedAt", Timestamp.from(publishedAt)));
    }

    public void scheduleRetry(UUID eventId, int attempts, Instant nextAttemptAt, String errorType) {
        jdbc.update("""
                UPDATE rollout_event_outbox
                SET attempts = :attempts, next_attempt_at = :nextAttemptAt, last_error = :lastError
                WHERE event_id = :eventId AND published_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("attempts", attempts)
                .addValue("nextAttemptAt", Timestamp.from(nextAttemptAt))
                .addValue("lastError", errorType));
    }

    public record PendingEvent(UUID eventId, String messageKey, String payload, int attempts) {
    }
}
