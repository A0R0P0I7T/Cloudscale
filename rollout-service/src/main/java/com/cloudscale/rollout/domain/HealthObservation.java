package com.cloudscale.rollout.domain;

import java.time.Instant;

public record HealthObservation(
        long requestCount,
        double errorRate,
        double latencyMs,
        Instant observedAt
) {
}
