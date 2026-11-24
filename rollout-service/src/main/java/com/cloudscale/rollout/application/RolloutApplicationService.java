package com.cloudscale.rollout.application;

import com.cloudscale.contracts.rollout.RolloutEventType;
import com.cloudscale.rollout.api.RolloutRequests;
import com.cloudscale.rollout.api.RolloutResponses;
import com.cloudscale.rollout.domain.HealthObservation;
import com.cloudscale.rollout.domain.InvalidRolloutTransitionException;
import com.cloudscale.rollout.domain.Rollout;
import com.cloudscale.rollout.domain.RolloutAction;
import com.cloudscale.rollout.domain.RolloutHealthPolicy;
import com.cloudscale.rollout.domain.RolloutRules;
import com.cloudscale.rollout.domain.RolloutStage;
import com.cloudscale.rollout.domain.RolloutStageStatus;
import com.cloudscale.rollout.domain.RolloutStatus;
import com.cloudscale.rollout.domain.StageDefinition;
import com.cloudscale.rollout.domain.StableRolloutBucketer;
import com.cloudscale.rollout.events.RolloutEventFactory;
import com.cloudscale.rollout.events.RolloutEventPublisher;
import com.cloudscale.rollout.persistence.RolloutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RolloutApplicationService {
    private final RolloutRepository repository;
    private final RolloutEventPublisher eventPublisher;
    private final RolloutRules rules;
    private final StableRolloutBucketer bucketer;

    public RolloutApplicationService(
            RolloutRepository repository,
            RolloutEventPublisher eventPublisher,
            RolloutRules rules,
            StableRolloutBucketer bucketer
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.rules = rules;
        this.bucketer = bucketer;
    }

    @Transactional
    public RolloutResponses.RolloutResponse create(
            UUID organizationId,
            RolloutRequests.CreateRolloutRequest request,
            String correlationId
    ) {
        List<StageDefinition> definitions = request.stages().stream()
                .map(stage -> new StageDefinition(stage.targetPercentage(), stage.durationMinutes()))
                .toList();
        rules.validateStages(definitions);
        RolloutHealthPolicy healthPolicy = healthPolicy(request.healthPolicy());
        rules.validateHealthPolicy(healthPolicy);

        Instant now = Instant.now();
        UUID rolloutId = UUID.randomUUID();
        Rollout rollout = new Rollout(rolloutId, organizationId, request.featureFlagId(),
                request.environmentId(), RolloutStatus.DRAFT, 0, now, now, null);
        List<RolloutStage> stages = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            StageDefinition definition = definitions.get(index);
            stages.add(new RolloutStage(UUID.randomUUID(), rolloutId, index + 1,
                    definition.targetPercentage(), definition.durationMinutes(),
                    RolloutStageStatus.PENDING, now, null, null));
        }
        repository.insert(rollout, stages, healthPolicy);
        publish(rollout, RolloutEventType.ROLLOUT_CREATED, correlationId);
        return RolloutResponses.RolloutResponse.from(rollout, stages);
    }

    @Transactional(readOnly = true)
    public List<RolloutResponses.RolloutResponse> list(UUID organizationId) {
        return repository.findAllByOrganization(organizationId).stream()
                .map(rollout -> RolloutResponses.RolloutResponse.from(
                        rollout, repository.findStages(rollout.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RolloutResponses.RolloutResponse get(UUID organizationId, UUID rolloutId) {
        return RolloutResponses.RolloutResponse.from(require(organizationId, rolloutId),
                repository.findStages(rolloutId));
    }

    @Transactional
    public RolloutResponses.RolloutResponse start(UUID organizationId, UUID rolloutId, String correlationId) {
        Rollout rollout = lock(organizationId, rolloutId);
        RolloutStatus nextStatus = rules.transition(rollout.status(), RolloutAction.START);
        RolloutStage firstStage = repository.findStages(rolloutId).stream().findFirst()
                .orElseThrow(() -> new InvalidRolloutTransitionException("A rollout requires at least one stage"));
        Instant now = Instant.now();
        repository.updateStage(stage(firstStage, RolloutStageStatus.IN_PROGRESS, now, null));
        Rollout updated = rollout(rollout, nextStatus, firstStage.targetPercentage(),
                rollout.lastAutoPausedAt(), now);
        repository.update(updated);
        publish(updated, RolloutEventType.ROLLOUT_STARTED, correlationId);
        return response(updated);
    }

    @Transactional
    public RolloutResponses.RolloutResponse pause(UUID organizationId, UUID rolloutId, String correlationId) {
        Rollout rollout = lock(organizationId, rolloutId);
        RolloutStatus nextStatus = rules.transition(rollout.status(), RolloutAction.PAUSE);
        Instant now = Instant.now();
        Rollout updated = rollout(rollout, nextStatus, rollout.currentPercentage(), rollout.lastAutoPausedAt(), now);
        repository.update(updated);
        publish(updated, RolloutEventType.ROLLOUT_PAUSED, correlationId);
        return response(updated);
    }

    @Transactional
    public RolloutResponses.RolloutResponse resume(UUID organizationId, UUID rolloutId, String correlationId) {
        Rollout rollout = lock(organizationId, rolloutId);
        RolloutStatus nextStatus = rules.transition(rollout.status(), RolloutAction.RESUME);
        RolloutHealthPolicy policy = repository.findHealthPolicy(rolloutId).orElse(null);
        Instant now = Instant.now();
        if (policy != null && !rules.cooldownElapsed(
                rollout.lastAutoPausedAt(), policy.cooldownSeconds(), now)) {
            throw new InvalidRolloutTransitionException("Health-policy cooldown has not elapsed");
        }
        RolloutStage currentStage = repository.findCurrentStage(rolloutId)
                .orElseThrow(() -> new InvalidRolloutTransitionException("The paused rollout has no active stage"));
        Instant effectiveStartedAt = rules.effectiveStageStartedAt(
                currentStage.startedAt(), rollout.updatedAt(), now);
        repository.updateStage(stage(currentStage, RolloutStageStatus.IN_PROGRESS, effectiveStartedAt, null));
        Rollout updated = rollout(rollout, nextStatus, rollout.currentPercentage(), rollout.lastAutoPausedAt(), now);
        repository.update(updated);
        publish(updated, RolloutEventType.ROLLOUT_RESUMED, correlationId);
        return response(updated);
    }

    @Transactional
    public RolloutResponses.RolloutResponse advance(UUID organizationId, UUID rolloutId, String correlationId) {
        Rollout rollout = lock(organizationId, rolloutId);
        rules.transition(rollout.status(), RolloutAction.ADVANCE);
        RolloutStage currentStage = repository.findCurrentStage(rolloutId)
                .orElseThrow(() -> new InvalidRolloutTransitionException("The rollout has no active stage"));
        Instant now = Instant.now();
        Instant eligibleAt = currentStage.startedAt().plus(Duration.ofMinutes(currentStage.durationMinutes()));
        if (now.isBefore(eligibleAt)) {
            throw new InvalidRolloutTransitionException("The active stage duration has not elapsed");
        }

        repository.updateStage(stage(currentStage, RolloutStageStatus.COMPLETED,
                currentStage.startedAt(), now));
        RolloutStage nextStage = repository.findNextStage(rolloutId, currentStage.stageOrder()).orElse(null);
        RolloutStatus nextStatus = nextStage == null ? RolloutStatus.COMPLETED : RolloutStatus.IN_PROGRESS;
        int nextPercentage = nextStage == null ? currentStage.targetPercentage() : nextStage.targetPercentage();
        if (nextStage != null) {
            repository.updateStage(stage(nextStage, RolloutStageStatus.IN_PROGRESS, now, null));
        }
        Rollout updated = rollout(rollout, nextStatus, nextPercentage, rollout.lastAutoPausedAt(), now);
        repository.update(updated);
        publish(updated, nextStage == null
                ? RolloutEventType.ROLLOUT_COMPLETED
                : RolloutEventType.ROLLOUT_STAGE_ADVANCED, correlationId);
        return response(updated);
    }

    @Transactional
    public RolloutResponses.RolloutResponse rollback(UUID organizationId, UUID rolloutId, String correlationId) {
        Rollout rollout = lock(organizationId, rolloutId);
        RolloutStatus nextStatus = rules.transition(rollout.status(), RolloutAction.ROLLBACK);
        Instant now = Instant.now();
        repository.skipRemainingStages(rolloutId, now);
        Rollout updated = rollout(rollout, nextStatus, 0, rollout.lastAutoPausedAt(), now);
        repository.update(updated);
        publish(updated, RolloutEventType.ROLLOUT_ROLLED_BACK, correlationId);
        return response(updated);
    }

    @Transactional
    public RolloutResponses.ObservationResponse observe(
            UUID organizationId,
            UUID rolloutId,
            RolloutRequests.HealthObservationRequest request,
            String correlationId
    ) {
        Rollout rollout = lock(organizationId, rolloutId);
        if (rollout.status() != RolloutStatus.IN_PROGRESS && rollout.status() != RolloutStatus.PAUSED) {
            throw new InvalidRolloutTransitionException("Health observations require an active rollout");
        }
        validateObservation(request);
        Instant now = Instant.now();
        HealthObservation observation = new HealthObservation(
                request.requestCount(), request.errorRate(), request.latencyMs(), request.observedAt());
        UUID observationId = UUID.randomUUID();
        repository.insertObservation(observationId, rolloutId, observation, now);

        boolean automaticallyPaused = false;
        Rollout updated = rollout;
        RolloutHealthPolicy policy = repository.findHealthPolicy(rolloutId).orElse(null);
        if (rollout.status() == RolloutStatus.IN_PROGRESS && policy != null
                && rules.shouldPause(policy, observation, now)
                && rules.cooldownElapsed(rollout.lastAutoPausedAt(), policy.cooldownSeconds(), now)) {
            updated = rollout(rollout, RolloutStatus.PAUSED, rollout.currentPercentage(), now, now);
            repository.update(updated);
            automaticallyPaused = true;
        }
        publish(updated, RolloutEventType.HEALTH_OBSERVATION_RECORDED, correlationId);
        return new RolloutResponses.ObservationResponse(
                observationId, rolloutId, automaticallyPaused, updated.status().name(), now);
    }

    @Transactional(readOnly = true)
    public RolloutResponses.AssignmentResponse assignment(
            UUID organizationId,
            RolloutRequests.AssignmentRequest request
    ) {
        int bucket = bucketer.bucket(request.userId().trim(), request.featureFlagId(), request.environmentId());
        Rollout rollout = repository.findForAssignment(organizationId,
                request.featureFlagId(), request.environmentId()).orElse(null);
        if (rollout == null) {
            return new RolloutResponses.AssignmentResponse(null, bucket, 0, false, null);
        }
        boolean assigned = bucket < rollout.currentPercentage() * 100;
        return new RolloutResponses.AssignmentResponse(
                rollout.id(), bucket, rollout.currentPercentage(), assigned, rollout.status());
    }

    private RolloutHealthPolicy healthPolicy(RolloutRequests.HealthPolicyRequest request) {
        if (request == null) {
            return null;
        }
        return new RolloutHealthPolicy(request.metric(), request.threshold(),
                request.minimumRequestCount() == null ? 100 : request.minimumRequestCount(),
                request.evaluationWindowSeconds(), request.cooldownSeconds());
    }

    private void validateObservation(RolloutRequests.HealthObservationRequest request) {
        if (request.observedAt() == null || request.observedAt().isAfter(Instant.now())
                || request.requestCount() < 0 || !Double.isFinite(request.errorRate())
                || request.errorRate() < 0 || request.errorRate() > 1
                || !Double.isFinite(request.latencyMs()) || request.latencyMs() < 0) {
            throw new IllegalArgumentException("Health observation values are invalid");
        }
    }

    private Rollout require(UUID organizationId, UUID rolloutId) {
        return repository.findByOrganizationAndId(organizationId, rolloutId)
                .orElseThrow(RolloutNotFoundException::new);
    }

    private Rollout lock(UUID organizationId, UUID rolloutId) {
        return repository.lockByOrganizationAndId(organizationId, rolloutId)
                .orElseThrow(RolloutNotFoundException::new);
    }

    private RolloutResponses.RolloutResponse response(Rollout rollout) {
        return RolloutResponses.RolloutResponse.from(rollout, repository.findStages(rollout.id()));
    }

    private void publish(Rollout rollout, RolloutEventType eventType, String correlationId) {
        String effectiveCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;
        eventPublisher.publish(RolloutEventFactory.from(rollout, eventType, effectiveCorrelationId));
    }

    private Rollout rollout(
            Rollout current,
            RolloutStatus status,
            int percentage,
            Instant lastAutoPausedAt,
            Instant updatedAt
    ) {
        return new Rollout(current.id(), current.organizationId(), current.featureFlagId(),
                current.environmentId(), status, percentage, current.createdAt(), updatedAt, lastAutoPausedAt);
    }

    private RolloutStage stage(
            RolloutStage current,
            RolloutStageStatus status,
            Instant startedAt,
            Instant completedAt
    ) {
        return new RolloutStage(current.id(), current.rolloutId(), current.stageOrder(),
                current.targetPercentage(), current.durationMinutes(), status, current.createdAt(),
                startedAt, completedAt);
    }
}
