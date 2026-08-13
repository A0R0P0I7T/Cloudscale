package com.cloudscale.config.api;

import com.cloudscale.config.api.dto.PublishedFeatureConfigurationResponse;
import com.cloudscale.config.application.FeatureVersionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectKey}/environments/{environmentKey}/features")
public class PublishedConfigurationController {
    private final FeatureVersionService featureVersionService;

    public PublishedConfigurationController(FeatureVersionService featureVersionService) {
        this.featureVersionService = featureVersionService;
    }

    @GetMapping("/{featureKey}/evaluation-config")
    public PublishedFeatureConfigurationResponse getEvaluationConfiguration(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey,
            @PathVariable String featureKey
    ) {
        return featureVersionService.getEvaluationConfiguration(
                organizationId, projectKey, environmentKey, featureKey);
    }
}
