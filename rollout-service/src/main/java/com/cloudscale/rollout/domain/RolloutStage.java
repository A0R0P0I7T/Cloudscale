package com.cloudscale.rollout.domain;

import java.time.Instant;
import java.util.UUID;

public record RolloutStage(
        UUID id,
        UUID rolloutId,
        int stageOrder,
        int targetPercentage,
        int durationMinutes,
        RolloutStageStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
}
