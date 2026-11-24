package com.cloudscale.rollout.domain;

public record StageDefinition(int targetPercentage, int durationMinutes) {
}
