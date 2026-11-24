package com.cloudscale.audit.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID organizationId,
        UUID entityId,
        String entityType,
        String source,
        String correlationId,
        JsonNode payload,
        Instant occurredAt,
        Instant recordedAt
) {
}
