package com.cloudscale.config.error;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        Instant timestamp,
        String correlationId
) {
}
