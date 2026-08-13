package com.cloudscale.evaluation.error;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        Instant timestamp,
        String correlationId
) {
}
