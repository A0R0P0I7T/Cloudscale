package com.cloudscale.rollout.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolloutRulesTest {
    private final RolloutRules rules = new RolloutRules();

    @Test
    void requiresStrictlyAscendingStagesEndingAtOneHundredPercent() {
        assertThatThrownBy(() -> rules.validateStages(List.of(
                new StageDefinition(25, 10),
                new StageDefinition(20, 10),
                new StageDefinition(100, 30))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.validateStages(List.of(
                new StageDefinition(10, 10),
                new StageDefinition(90, 30))))
                .isInstanceOf(IllegalArgumentException.class);

        rules.validateStages(List.of(
                new StageDefinition(0, 10),
                new StageDefinition(25, 10),
                new StageDefinition(100, 30)));
    }

    @Test
    void permitsOnlyValidLifecycleTransitions() {
        assertThat(rules.transition(RolloutStatus.DRAFT, RolloutAction.START))
                .isEqualTo(RolloutStatus.IN_PROGRESS);
        assertThat(rules.transition(RolloutStatus.IN_PROGRESS, RolloutAction.PAUSE))
                .isEqualTo(RolloutStatus.PAUSED);
        assertThat(rules.transition(RolloutStatus.PAUSED, RolloutAction.RESUME))
                .isEqualTo(RolloutStatus.IN_PROGRESS);
        assertThat(rules.transition(RolloutStatus.IN_PROGRESS, RolloutAction.ROLLBACK))
                .isEqualTo(RolloutStatus.ROLLED_BACK);
        assertThatThrownBy(() -> rules.transition(RolloutStatus.DRAFT, RolloutAction.PAUSE))
                .isInstanceOf(InvalidRolloutTransitionException.class);
        assertThatThrownBy(() -> rules.transition(RolloutStatus.COMPLETED, RolloutAction.RESUME))
                .isInstanceOf(InvalidRolloutTransitionException.class);
    }

    @Test
    void pausesOnlyAfterHealthThresholdAndMinimumSampleWindowAreMet() {
        Instant now = Instant.parse("2026-11-23T12:00:00Z");
        RolloutHealthPolicy policy = new RolloutHealthPolicy(
                HealthMetric.ERROR_RATE, 0.05, 100, 300, 600);

        assertThat(rules.shouldPause(policy,
                new HealthObservation(99, 0.10, 50, now), now)).isFalse();
        assertThat(rules.shouldPause(policy,
                new HealthObservation(500, 0.01, 50, now), now)).isFalse();
        assertThat(rules.shouldPause(policy,
                new HealthObservation(500, 0.10, 50, now.minusSeconds(301)), now)).isFalse();
        assertThat(rules.shouldPause(policy,
                new HealthObservation(100, 0.06, 50, now), now)).isTrue();
    }

    @Test
    void latencyPolicyPausesOnlyForHighEnoughSampledLatency() {
        Instant now = Instant.parse("2026-11-23T12:00:00Z");
        RolloutHealthPolicy policy = new RolloutHealthPolicy(
                HealthMetric.LATENCY_MS, 250, 50, 120, 300);

        assertThat(rules.shouldPause(policy,
                new HealthObservation(49, 0.2, 500, now), now)).isFalse();
        assertThat(rules.shouldPause(policy,
                new HealthObservation(50, 0.2, 251, now), now)).isTrue();
    }

    @Test
    void excludesPausedTimeFromStageDuration() {
        Instant startedAt = Instant.parse("2026-11-23T12:00:00Z");
        Instant pausedAt = startedAt.plusSeconds(120);
        Instant resumedAt = pausedAt.plusSeconds(600);

        assertThat(rules.effectiveStageStartedAt(startedAt, pausedAt, resumedAt))
                .isEqualTo(startedAt.plusSeconds(600));
    }
}
