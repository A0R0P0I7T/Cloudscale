package com.cloudscale.config.api;

import com.cloudscale.config.api.dto.CreateProjectRequest;
import com.cloudscale.config.api.dto.ProjectResponse;
import com.cloudscale.config.application.ProjectService;
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
@RequestMapping("/api/v1/organizations/{organizationId}/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        ProjectResponse response = ProjectResponse.from(projectService.create(organizationId, request));
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + organizationId + "/projects/" + response.key()))
                .body(response);
    }

    @GetMapping
    public List<ProjectResponse> list(@PathVariable UUID organizationId) {
        return projectService.list(organizationId).stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{projectKey}")
    public ProjectResponse get(@PathVariable UUID organizationId, @PathVariable String projectKey) {
        return ProjectResponse.from(projectService.get(organizationId, projectKey));
    }
}
