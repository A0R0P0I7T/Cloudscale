package com.cloudscale.rollout.domain;

public record RolloutHealthPolicy(
        HealthMetric metric,
        double threshold,
        int minimumRequestCount,
        int evaluationWindowSeconds,
        int cooldownSeconds
) {
}
