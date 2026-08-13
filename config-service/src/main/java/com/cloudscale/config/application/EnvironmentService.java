package com.cloudscale.config.application;

import com.cloudscale.config.api.dto.CreateEnvironmentRequest;
import com.cloudscale.config.domain.Environment;
import com.cloudscale.config.domain.Project;
import com.cloudscale.config.error.DuplicateResourceException;
import com.cloudscale.config.error.ResourceNotFoundException;
import com.cloudscale.config.persistence.EnvironmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EnvironmentService {
    private final ProjectService projectService;
    private final EnvironmentRepository environmentRepository;

    public EnvironmentService(ProjectService projectService, EnvironmentRepository environmentRepository) {
        this.projectService = projectService;
        this.environmentRepository = environmentRepository;
    }

    @Transactional
    public Environment create(UUID organizationId, String projectKey, CreateEnvironmentRequest request) {
        Project project = projectService.get(organizationId, projectKey);
        if (environmentRepository.findByProjectAndKey(project.id(), request.key()).isPresent()) {
            throw new DuplicateResourceException("ENVIRONMENT_ALREADY_EXISTS",
                    "Environment '" + request.key() + "' already exists");
        }
        Instant now = Instant.now();
        Environment environment = new Environment(UUID.randomUUID(), project.id(), request.key(), request.name(),
                request.description(), now, now);
        try {
            environmentRepository.insert(environment);
            return environment;
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("ENVIRONMENT_ALREADY_EXISTS",
                    "Environment '" + request.key() + "' already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<Environment> list(UUID organizationId, String projectKey) {
        Project project = projectService.get(organizationId, projectKey);
        return environmentRepository.findAllByProject(project.id());
    }

    @Transactional(readOnly = true)
    public Environment get(UUID organizationId, String projectKey, String environmentKey) {
        Project project = projectService.get(organizationId, projectKey);
        return environmentRepository.findByProjectAndKey(project.id(), environmentKey)
                .orElseThrow(() -> new ResourceNotFoundException("ENVIRONMENT_NOT_FOUND",
                        "Environment '" + environmentKey + "' was not found"));
    }
}
