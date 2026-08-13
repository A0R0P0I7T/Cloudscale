package com.cloudscale.config.api;

import com.cloudscale.config.api.dto.CreateEnvironmentRequest;
import com.cloudscale.config.api.dto.EnvironmentResponse;
import com.cloudscale.config.application.EnvironmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectKey}/environments")
public class EnvironmentController {
    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @PostMapping
    public ResponseEntity<EnvironmentResponse> create(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @Valid @RequestBody CreateEnvironmentRequest request
    ) {
        EnvironmentResponse response = EnvironmentResponse.from(environmentService.create(organizationId, projectKey, request));
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + organizationId
                        + "/projects/" + projectKey + "/environments/" + response.key()))
                .body(response);
    }

    @GetMapping
    public List<EnvironmentResponse> list(@PathVariable UUID organizationId, @PathVariable String projectKey) {
        return environmentService.list(organizationId, projectKey).stream().map(EnvironmentResponse::from).toList();
    }

    @GetMapping("/{environmentKey}")
    public EnvironmentResponse get(
            @PathVariable UUID organizationId,
            @PathVariable String projectKey,
            @PathVariable String environmentKey
    ) {
        return EnvironmentResponse.from(environmentService.get(organizationId, projectKey, environmentKey));
    }
}
