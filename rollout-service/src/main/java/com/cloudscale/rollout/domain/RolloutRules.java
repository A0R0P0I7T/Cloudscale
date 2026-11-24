package com.cloudscale.rollout.domain;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

public class RolloutRules {
    public void validateStages(List<StageDefinition> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("At least one rollout stage is required");
        }
        if (stages.size() > 20) {
            throw new IllegalArgumentException("A rollout can have at most 20 stages");
        }
        int previous = -1;
        for (StageDefinition stage : stages) {
            if (stage == null || stage.targetPercentage() < 0 || stage.targetPercentage() > 100
                    || stage.durationMinutes() <= 0 || stage.targetPercentage() <= previous) {
                throw new IllegalArgumentException("Rollout stages must have increasing percentages from 0 to 100 and positive duration");
            }
            previous = stage.targetPercentage();
        }
        if (previous != 100) {
            throw new IllegalArgumentException("The final rollout stage must target 100 percent");
        }
    }

    public void validateHealthPolicy(RolloutHealthPolicy policy) {
        if (policy == null) {
            return;
        }
        if (policy.metric() == null || !Double.isFinite(policy.threshold())
                || policy.minimumRequestCount() <= 0 || policy.evaluationWindowSeconds() <= 0
                || policy.cooldownSeconds() <= 0) {
            throw new IllegalArgumentException("Health policy values must be finite and positive");
        }
        if (policy.metric() == HealthMetric.ERROR_RATE
                && (policy.threshold() <= 0 || policy.threshold() > 1)) {
            throw new IllegalArgumentException("Error-rate threshold must be greater than zero and at most one");
        }
        if (policy.metric() == HealthMetric.LATENCY_MS && policy.threshold() <= 0) {
            throw new IllegalArgumentException("Latency threshold must be greater than zero");
        }
    }

    public RolloutStatus transition(RolloutStatus current, RolloutAction action) {
        if (current == null || action == null) {
            throw new InvalidRolloutTransitionException("Rollout state or action is missing");
        }
        return switch (current) {
            case DRAFT -> {
                if (action == RolloutAction.START) {
                    yield RolloutStatus.IN_PROGRESS;
                }
                throw invalid(current, action);
            }
            case IN_PROGRESS -> switch (action) {
                case PAUSE -> RolloutStatus.PAUSED;
                case ADVANCE -> RolloutStatus.IN_PROGRESS;
                case COMPLETE -> RolloutStatus.COMPLETED;
                case ROLLBACK -> RolloutStatus.ROLLED_BACK;
                default -> throw invalid(current, action);
            };
            case PAUSED -> switch (action) {
                case RESUME -> RolloutStatus.IN_PROGRESS;
                case ROLLBACK -> RolloutStatus.ROLLED_BACK;
                default -> throw invalid(current, action);
            };
            case COMPLETED, ROLLED_BACK -> throw invalid(current, action);
        };
    }

    public boolean shouldPause(RolloutHealthPolicy policy, HealthObservation observation, Instant now) {
        if (policy == null || observation == null || now == null
                || observation.observedAt() == null
                || observation.requestCount() < policy.minimumRequestCount()
                || observation.observedAt().isAfter(now)
                || observation.observedAt().isBefore(now.minusSeconds(policy.evaluationWindowSeconds()))) {
            return false;
        }
        return switch (policy.metric()) {
            case ERROR_RATE -> observation.errorRate() > policy.threshold();
            case LATENCY_MS -> observation.latencyMs() > policy.threshold();
        };
    }

    public boolean cooldownElapsed(Instant lastAutomaticPause, int cooldownSeconds, Instant now) {
        return lastAutomaticPause == null || !now.isBefore(lastAutomaticPause.plusSeconds(cooldownSeconds));
    }

    public Instant effectiveStageStartedAt(Instant startedAt, Instant pausedAt, Instant resumedAt) {
        if (startedAt == null || pausedAt == null || resumedAt == null || resumedAt.isBefore(pausedAt)) {
            throw new IllegalArgumentException("Stage pause timestamps are invalid");
        }
        return startedAt.plus(Duration.between(pausedAt, resumedAt));
    }

    private InvalidRolloutTransitionException invalid(RolloutStatus status, RolloutAction action) {
        return new InvalidRolloutTransitionException(
                "Action " + action + " is not allowed while rollout is " + status);
    }
}
