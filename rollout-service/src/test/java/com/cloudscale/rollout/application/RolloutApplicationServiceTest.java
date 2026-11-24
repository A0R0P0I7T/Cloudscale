package com.cloudscale.rollout.application;

import com.cloudscale.contracts.rollout.RolloutEvent;
import com.cloudscale.contracts.rollout.RolloutEventType;
import com.cloudscale.rollout.api.RolloutRequests;
import com.cloudscale.rollout.domain.HealthMetric;
import com.cloudscale.rollout.domain.Rollout;
import com.cloudscale.rollout.domain.RolloutHealthPolicy;
import com.cloudscale.rollout.domain.RolloutRules;
import com.cloudscale.rollout.domain.RolloutStatus;
import com.cloudscale.rollout.domain.StableRolloutBucketer;
import com.cloudscale.rollout.events.RolloutEventPublisher;
import com.cloudscale.rollout.persistence.RolloutRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RolloutApplicationServiceTest {
    @Test
    void rejectsInvalidStagesBeforeWritingRollout() {
        RolloutRepository repository = mock(RolloutRepository.class);
        RolloutEventPublisher eventPublisher = mock(RolloutEventPublisher.class);
        RolloutApplicationService service = service(repository, eventPublisher);
        RolloutRequests.CreateRolloutRequest request = new RolloutRequests.CreateRolloutRequest(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new RolloutRequests.StageRequest(50, 5),
                        new RolloutRequests.StageRequest(25, 10),
                        new RolloutRequests.StageRequest(100, 20)),
                null);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request, "trace-1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, eventPublisher);
    }

    @Test
    void assignmentUsesStableBucketAndCurrentPercentage() {
        RolloutRepository repository = mock(RolloutRepository.class);
        RolloutEventPublisher eventPublisher = mock(RolloutEventPublisher.class);
        RolloutApplicationService service = service(repository, eventPublisher);
        UUID organizationId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        Rollout rollout = rollout(organizationId, featureId, environmentId, RolloutStatus.IN_PROGRESS, 25);
        when(repository.findForAssignment(organizationId, featureId, environmentId))
                .thenReturn(Optional.of(rollout));

        var result = service.assignment(organizationId,
                new RolloutRequests.AssignmentRequest(featureId, environmentId, "stable-user"));

        assertThat(result.bucket()).isBetween(0, 9_999);
        assertThat(result.assigned()).isEqualTo(result.bucket() < 2_500);
        assertThat(result.percentage()).isEqualTo(25);
        assertThat(service.assignment(organizationId,
                new RolloutRequests.AssignmentRequest(featureId, environmentId, "stable-user")).bucket())
                .isEqualTo(result.bucket());
    }

    @Test
    void lowSampleObservationDoesNotPauseActiveRollout() {
        RolloutRepository repository = mock(RolloutRepository.class);
        RolloutEventPublisher eventPublisher = mock(RolloutEventPublisher.class);
        RolloutApplicationService service = service(repository, eventPublisher);
        UUID organizationId = UUID.randomUUID();
        Rollout rollout = rollout(organizationId, UUID.randomUUID(), UUID.randomUUID(),
                RolloutStatus.IN_PROGRESS, 25);
        when(repository.lockByOrganizationAndId(organizationId, rollout.id())).thenReturn(Optional.of(rollout));
        when(repository.findHealthPolicy(rollout.id())).thenReturn(Optional.of(
                new RolloutHealthPolicy(HealthMetric.ERROR_RATE, 0.05, 100, 300, 300)));

        var result = service.observe(organizationId, rollout.id(),
                new RolloutRequests.HealthObservationRequest(99, 0.9, 100, Instant.now()), "trace-1");

        assertThat(result.automaticallyPaused()).isFalse();
        assertThat(result.rolloutStatus()).isEqualTo("IN_PROGRESS");
        verify(repository, never()).update(any(Rollout.class));
    }

    @Test
    void highErrorRateWithEnoughSamplesPausesWithoutRollback() {
        RolloutRepository repository = mock(RolloutRepository.class);
        RolloutEventPublisher eventPublisher = mock(RolloutEventPublisher.class);
        RolloutApplicationService service = service(repository, eventPublisher);
        UUID organizationId = UUID.randomUUID();
        Rollout rollout = rollout(organizationId, UUID.randomUUID(), UUID.randomUUID(),
                RolloutStatus.IN_PROGRESS, 25);
        when(repository.lockByOrganizationAndId(organizationId, rollout.id())).thenReturn(Optional.of(rollout));
        when(repository.findHealthPolicy(rollout.id())).thenReturn(Optional.of(
                new RolloutHealthPolicy(HealthMetric.ERROR_RATE, 0.05, 100, 300, 300)));

        var result = service.observe(organizationId, rollout.id(),
                new RolloutRequests.HealthObservationRequest(100, 0.10, 100, Instant.now()), "trace-1");

        assertThat(result.automaticallyPaused()).isTrue();
        assertThat(result.rolloutStatus()).isEqualTo("PAUSED");
        ArgumentCaptor<Rollout> updated = ArgumentCaptor.forClass(Rollout.class);
        verify(repository).update(updated.capture());
        assertThat(updated.getValue().status()).isEqualTo(RolloutStatus.PAUSED);
        assertThat(updated.getValue().currentPercentage()).isEqualTo(25);
        ArgumentCaptor<RolloutEvent> event = ArgumentCaptor.forClass(RolloutEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(RolloutEventType.HEALTH_OBSERVATION_RECORDED);
        assertThat(event.getValue().status()).isEqualTo("PAUSED");
    }

    private RolloutApplicationService service(RolloutRepository repository, RolloutEventPublisher eventPublisher) {
        return new RolloutApplicationService(repository, eventPublisher,
                new RolloutRules(), new StableRolloutBucketer());
    }

    private Rollout rollout(UUID organizationId, UUID featureId, UUID environmentId,
                            RolloutStatus status, int percentage) {
        Instant now = Instant.now();
        return new Rollout(UUID.randomUUID(), organizationId, featureId, environmentId,
                status, percentage, now, now, null);
    }
}
