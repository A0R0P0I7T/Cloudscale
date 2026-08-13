package com.cloudscale.config.api;

import com.cloudscale.config.api.dto.CreateFeatureVersionRequest;
import com.cloudscale.config.api.dto.FeatureVersionResponse;
import com.cloudscale.config.application.FeatureVersionService;
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
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectKey}/environments/{environmentKey}/features/{featureKey}/versions")
public class FeatureVersionController {
    private final FeatureVersionService featureVersionService;

    public FeatureVersionController(FeatureVersionService featureVersionService) {
        this.featureVersionService = featureVersionService;
    }

    @PostMapping
    public ResponseEntity<FeatureVersionResponse> create(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey,
            @PathVariable String featureKey,
            @Valid @RequestBody CreateFeatureVersionRequest request
    ) {
        var version = featureVersionService.createDraft(organizationId, projectKey, environmentKey, featureKey, request);
        FeatureVersionResponse response = FeatureVersionResponse.from(version, featureVersionService.rules(version));
        URI location = URI.create("/api/v1/organizations/" + organizationId + "/projects/" + projectKey
                + "/environments/" + environmentKey + "/features/" + featureKey + "/versions/" + response.versionNumber());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<FeatureVersionResponse> list(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey,
            @PathVariable String featureKey
    ) {
        return featureVersionService.list(organizationId, projectKey, environmentKey, featureKey).stream()
                .map(version -> FeatureVersionResponse.from(version, featureVersionService.rules(version)))
                .toList();
    }

    @GetMapping("/{versionNumber}")
    public FeatureVersionResponse get(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey,
            @PathVariable String featureKey,
            @PathVariable int versionNumber
    ) {
        var version = featureVersionService.get(organizationId, projectKey, environmentKey, featureKey, versionNumber);
        return FeatureVersionResponse.from(version, featureVersionService.rules(version));
    }

    @PostMapping("/{versionNumber}/publish")
    public FeatureVersionResponse publish(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey,
            @PathVariable String featureKey,
            @PathVariable int versionNumber,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        var version = featureVersionService.publish(
                organizationId, projectKey, environmentKey, featureKey, versionNumber, correlationId);
        return FeatureVersionResponse.from(version, featureVersionService.rules(version));
    }
}
