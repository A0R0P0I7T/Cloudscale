package com.cloudscale.config.api;

import com.cloudscale.config.api.dto.CreateFeatureFlagRequest;
import com.cloudscale.config.api.dto.FeatureFlagResponse;
import com.cloudscale.config.api.dto.UpdateFeatureFlagRequest;
import com.cloudscale.config.application.FeatureFlagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectKey}/environments/{environmentKey}/features")
public class FeatureFlagController {
    private final FeatureFlagService featureFlagService;

    public FeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @PostMapping
    public ResponseEntity<FeatureFlagResponse> create(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey,
            @Valid @RequestBody CreateFeatureFlagRequest request
    ) {
        FeatureFlagResponse response = FeatureFlagResponse.from(
                featureFlagService.create(organizationId, projectKey, environmentKey, request));
        URI location = URI.create("/api/v1/organizations/" + organizationId + "/projects/" + projectKey
                + "/environments/" + environmentKey + "/features/" + response.key());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<FeatureFlagResponse> list(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey
    ) {
        return featureFlagService.list(organizationId, projectKey, environmentKey)
                .stream().map(FeatureFlagResponse::from).toList();
    }

    @GetMapping("/{featureKey}")
    public FeatureFlagResponse get(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey,
            @PathVariable String featureKey
    ) {
        return FeatureFlagResponse.from(featureFlagService.get(organizationId, projectKey, environmentKey, featureKey));
    }

    @PatchMapping("/{featureKey}")
    public FeatureFlagResponse update(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey,
            @PathVariable String featureKey,
            @Valid @RequestBody UpdateFeatureFlagRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        return FeatureFlagResponse.from(featureFlagService.update(
                organizationId, projectKey, environmentKey, featureKey, request, correlationId));
    }
}
