package com.cloudscale.rollout.api;

import com.cloudscale.rollout.domain.HealthMetric;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RolloutRequests {
    private RolloutRequests() {
    }

    public record CreateRolloutRequest(
            @NotNull UUID featureFlagId,
            @NotNull UUID environmentId,
            @NotNull @Size(min = 1, max = 20) List<@Valid StageRequest> stages,
            @Valid HealthPolicyRequest healthPolicy
    ) {
    }

    public record StageRequest(
            @Min(0) @Max(100) int targetPercentage,
            @Positive int durationMinutes
    ) {
    }

    public record HealthPolicyRequest(
            @NotNull HealthMetric metric,
            @Positive double threshold,
            @Min(1) Integer minimumRequestCount,
            @Positive int evaluationWindowSeconds,
            @Positive int cooldownSeconds
    ) {
    }

    public record HealthObservationRequest(
            @PositiveOrZero long requestCount,
            @DecimalMin("0.0") @DecimalMax("1.0") double errorRate,
            @PositiveOrZero double latencyMs,
            @NotNull Instant observedAt
    ) {
    }

    public record AssignmentRequest(
            @NotNull UUID featureFlagId,
            @NotNull UUID environmentId,
            @NotBlank @Size(max = 255) String userId
    ) {
    }
}
