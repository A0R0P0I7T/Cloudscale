package com.cloudscale.evaluation.api;

import com.cloudscale.evaluation.application.EvaluationApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectKey}/environments/{environmentKey}/features")
public class EvaluationController {
    private final EvaluationApplicationService evaluationApplicationService;

    public EvaluationController(EvaluationApplicationService evaluationApplicationService) {
        this.evaluationApplicationService = evaluationApplicationService;
    }

    @PostMapping("/{featureKey}/evaluate")
    public EvaluationResponse evaluate(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey,
            @PathVariable String featureKey,
            @Valid @RequestBody EvaluationRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String effectiveCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;
        return evaluationApplicationService.evaluate(
                organizationId, projectKey, environmentKey, featureKey, request,
                authorizationHeader, effectiveCorrelationId);
    }
}
