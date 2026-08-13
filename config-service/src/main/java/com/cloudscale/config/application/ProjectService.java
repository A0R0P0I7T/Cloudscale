package com.cloudscale.config.application;

import com.cloudscale.config.api.dto.CreateProjectRequest;
import com.cloudscale.config.domain.Project;
import com.cloudscale.config.error.DuplicateResourceException;
import com.cloudscale.config.error.ResourceNotFoundException;
import com.cloudscale.config.persistence.ProjectRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Project create(UUID organizationId, CreateProjectRequest request) {
        if (projectRepository.findByOrganizationAndKey(organizationId, request.key()).isPresent()) {
            throw new DuplicateResourceException("PROJECT_ALREADY_EXISTS", "Project '" + request.key() + "' already exists");
        }
        Instant now = Instant.now();
        Project project = new Project(UUID.randomUUID(), organizationId, request.key(), request.name(),
                request.description(), now, now);
        try {
            projectRepository.insert(project);
            return project;
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("PROJECT_ALREADY_EXISTS", "Project '" + request.key() + "' already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<Project> list(UUID organizationId) {
        return projectRepository.findAllByOrganization(organizationId);
    }

    @Transactional(readOnly = true)
    public Project get(UUID organizationId, String key) {
        return projectRepository.findByOrganizationAndKey(organizationId, key)
                .orElseThrow(() -> new ResourceNotFoundException("PROJECT_NOT_FOUND", "Project '" + key + "' was not found"));
    }

    @Transactional(readOnly = true)
    public Project getById(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PROJECT_NOT_FOUND", "Project was not found"));
    }
}
