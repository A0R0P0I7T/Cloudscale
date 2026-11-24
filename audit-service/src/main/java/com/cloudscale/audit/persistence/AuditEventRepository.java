package com.cloudscale.audit.persistence;

import com.cloudscale.audit.api.AuditEventResponse;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.rollout.RolloutEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AuditEventRepository {
    private static final String CONSUMER_GROUP = "cloudscale-audit-service";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditEventRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean recordConfigurationEvent(ConfigurationEvent event) {
        return record(event.eventId(), event.eventType().name(), event.eventVersion(), event.organizationId(),
                event.entityId(), event.entityType().name(), event.source(), event.correlationId(),
                event.occurredAt(), event);
    }

    @Transactional
    public boolean recordRolloutEvent(RolloutEvent event) {
        return record(event.eventId(), event.eventType().name(), event.eventVersion(), event.organizationId(),
                event.entityId(), event.entityType(), event.source(), event.correlationId(),
                event.occurredAt(), event);
    }

    public List<AuditEventResponse> findByOrganization(UUID organizationId, int limit, long offset) {
        return jdbc.query("""
                SELECT event_id, event_type, event_version, organization_id, entity_id, entity_type,
                       source, correlation_id, payload::text AS payload, occurred_at, recorded_at
                FROM audit_events
                WHERE organization_id = :organizationId
                ORDER BY occurred_at DESC, event_id DESC
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                (rs, rowNum) -> new AuditEventResponse(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getInt("event_version"),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("entity_id", UUID.class),
                        rs.getString("entity_type"),
                        rs.getString("source"),
                        rs.getString("correlation_id"),
                        readPayload(rs.getString("payload")),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getTimestamp("recorded_at").toInstant()));
    }

    private boolean record(
            UUID eventId,
            String eventType,
            int eventVersion,
            UUID organizationId,
            UUID entityId,
            String entityType,
            String source,
            String correlationId,
            Instant occurredAt,
            Object event
    ) {
        String payload = serialize(event);
        Instant recordedAt = Instant.now();
        int firstDelivery = jdbc.update("""
                INSERT INTO processed_events (event_id, consumer_group, processed_at)
                VALUES (:eventId, :consumerGroup, :processedAt)
                ON CONFLICT (event_id, consumer_group) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("consumerGroup", CONSUMER_GROUP)
                .addValue("processedAt", Timestamp.from(recordedAt)));
        if (firstDelivery == 0) {
            return false;
        }
        int inserted = jdbc.update("""
                INSERT INTO audit_events
                    (id, event_id, event_type, event_version, organization_id, entity_id, entity_type,
                     source, correlation_id, payload, occurred_at, recorded_at)
                VALUES (:id, :eventId, :eventType, :eventVersion, :organizationId, :entityId, :entityType,
                        :source, :correlationId, CAST(:payload AS jsonb), :occurredAt, :recordedAt)
                ON CONFLICT (event_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("eventId", eventId)
                .addValue("eventType", eventType)
                .addValue("eventVersion", eventVersion)
                .addValue("organizationId", organizationId)
                .addValue("entityId", entityId)
                .addValue("entityType", entityType)
                .addValue("source", source)
                .addValue("correlationId", correlationId)
                .addValue("payload", payload)
                .addValue("occurredAt", Timestamp.from(occurredAt))
                .addValue("recordedAt", Timestamp.from(recordedAt)));
        return inserted == 1;
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit event could not be serialized", exception);
        }
    }

    private JsonNode readPayload(String payload) throws SQLException {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Stored audit event payload is not valid JSON", exception);
        }
    }
}
