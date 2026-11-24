package com.cloudscale.audit.api;

import java.time.Instant;

public record AuditErrorResponse(String code, String message, Instant timestamp) {
}
