package com.cloudscale.rollout.api;

import com.cloudscale.rollout.domain.Rollout;
import com.cloudscale.rollout.domain.RolloutStage;
import com.cloudscale.rollout.domain.RolloutStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RolloutResponses {
    private RolloutResponses() {
    }

    public record RolloutResponse(
            UUID id,
            UUID organizationId,
            UUID featureFlagId,
            UUID environmentId,
            RolloutStatus status,
            int currentPercentage,
            List<StageResponse> stages,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static RolloutResponse from(Rollout rollout, List<RolloutStage> stages) {
            return new RolloutResponse(rollout.id(), rollout.organizationId(), rollout.featureFlagId(),
                    rollout.environmentId(), rollout.status(), rollout.currentPercentage(),
                    stages.stream().map(StageResponse::from).toList(),
                    rollout.createdAt(), rollout.updatedAt());
        }
    }

    public record StageResponse(
            int stageOrder,
            int targetPercentage,
            int durationMinutes,
            String status,
            Instant startedAt,
            Instant completedAt
    ) {
        public static StageResponse from(RolloutStage stage) {
            return new StageResponse(stage.stageOrder(), stage.targetPercentage(), stage.durationMinutes(),
                    stage.status().name(), stage.startedAt(), stage.completedAt());
        }
    }

    public record AssignmentResponse(
            UUID rolloutId,
            int bucket,
            int percentage,
            boolean assigned,
            RolloutStatus status
    ) {
    }

    public record ObservationResponse(
            UUID observationId,
            UUID rolloutId,
            boolean automaticallyPaused,
            String rolloutStatus,
            Instant recordedAt
    ) {
    }
}
