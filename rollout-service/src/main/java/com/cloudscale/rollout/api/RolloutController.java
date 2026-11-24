package com.cloudscale.rollout.api;

import com.cloudscale.rollout.application.RolloutApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/rollouts")
public class RolloutController {
    private final RolloutApplicationService rolloutService;

    public RolloutController(RolloutApplicationService rolloutService) {
        this.rolloutService = rolloutService;
    }

    @PostMapping
    public ResponseEntity<RolloutResponses.RolloutResponse> create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody RolloutRequests.CreateRolloutRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        RolloutResponses.RolloutResponse response = rolloutService.create(organizationId, request, correlationId);
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + organizationId
                + "/rollouts/" + response.id())).body(response);
    }

    @GetMapping
    public List<RolloutResponses.RolloutResponse> list(@PathVariable UUID organizationId) {
        return rolloutService.list(organizationId);
    }

    @GetMapping("/{rolloutId}")
    public RolloutResponses.RolloutResponse get(
            @PathVariable UUID organizationId,
            @PathVariable UUID rolloutId
    ) {
        return rolloutService.get(organizationId, rolloutId);
    }

    @PostMapping("/{rolloutId}/start")
    public RolloutResponses.RolloutResponse start(
            @PathVariable UUID organizationId,
            @PathVariable UUID rolloutId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return rolloutService.start(organizationId, rolloutId, correlationId);
    }

    @PostMapping("/{rolloutId}/pause")
    public RolloutResponses.RolloutResponse pause(
            @PathVariable UUID organizationId,
            @PathVariable UUID rolloutId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return rolloutService.pause(organizationId, rolloutId, correlationId);
    }

    @PostMapping("/{rolloutId}/resume")
    public RolloutResponses.RolloutResponse resume(
            @PathVariable UUID organizationId,
            @PathVariable UUID rolloutId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return rolloutService.resume(organizationId, rolloutId, correlationId);
    }

    @PostMapping("/{rolloutId}/advance")
    public RolloutResponses.RolloutResponse advance(
            @PathVariable UUID organizationId,
            @PathVariable UUID rolloutId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return rolloutService.advance(organizationId, rolloutId, correlationId);
    }

    @PostMapping("/{rolloutId}/rollback")
    public RolloutResponses.RolloutResponse rollback(
            @PathVariable UUID organizationId,
            @PathVariable UUID rolloutId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return rolloutService.rollback(organizationId, rolloutId, correlationId);
    }

    @PostMapping("/{rolloutId}/observations")
    public RolloutResponses.ObservationResponse observe(
            @PathVariable UUID organizationId,
            @PathVariable UUID rolloutId,
            @Valid @RequestBody RolloutRequests.HealthObservationRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return rolloutService.observe(organizationId, rolloutId, request, correlationId);
    }

    @PostMapping("/assignment")
    public RolloutResponses.AssignmentResponse assignment(
            @PathVariable UUID organizationId,
            @Valid @RequestBody RolloutRequests.AssignmentRequest request
    ) {
        return rolloutService.assignment(organizationId, request);
    }
}
