package com.cloudscale.rollout.events;

import com.cloudscale.contracts.rollout.RolloutEvent;
import com.cloudscale.contracts.rollout.RolloutEventType;
import com.cloudscale.rollout.domain.Rollout;

import java.time.Instant;
import java.util.UUID;

public final class RolloutEventFactory {
    private RolloutEventFactory() {
    }

    public static RolloutEvent from(Rollout rollout, RolloutEventType type, String correlationId) {
        return new RolloutEvent(
                UUID.randomUUID(),
                type,
                1,
                Instant.now(),
                rollout.organizationId(),
                rollout.id(),
                "ROLLOUT",
                "rollout-service",
                correlationId,
                rollout.id(),
                rollout.featureFlagId(),
                rollout.environmentId(),
                rollout.currentPercentage(),
                rollout.status().name());
    }
}
