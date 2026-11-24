package com.cloudscale.config.application;

import com.cloudscale.config.api.dto.CreateFeatureFlagRequest;
import com.cloudscale.config.api.dto.UpdateFeatureFlagRequest;
import com.cloudscale.config.domain.Environment;
import com.cloudscale.config.domain.FeatureFlag;
import com.cloudscale.config.domain.Project;
import com.cloudscale.config.error.DuplicateResourceException;
import com.cloudscale.config.error.ConfigurationException;
import com.cloudscale.config.error.ResourceNotFoundException;
import com.cloudscale.config.events.ConfigurationEventFactory;
import com.cloudscale.config.events.ConfigurationEventPublisher;
import com.cloudscale.config.persistence.FeatureFlagRepository;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FeatureFlagService {
    private final ProjectService projectService;
    private final EnvironmentService environmentService;
    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureVersionService featureVersionService;
    private final ConfigurationEventPublisher eventPublisher;

    public FeatureFlagService(
            ProjectService projectService,
            EnvironmentService environmentService,
            FeatureFlagRepository featureFlagRepository,
            FeatureVersionService featureVersionService,
            ConfigurationEventPublisher eventPublisher
    ) {
        this.projectService = projectService;
        this.environmentService = environmentService;
        this.featureFlagRepository = featureFlagRepository;
        this.featureVersionService = featureVersionService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FeatureFlag create(
            UUID organizationId,
            String projectKey,
            String environmentKey,
            CreateFeatureFlagRequest request
    ) {
        Environment environment = environmentService.get(organizationId, projectKey, environmentKey);
        if (featureFlagRepository.findByEnvironmentAndKey(environment.id(), request.key()).isPresent()) {
            throw new DuplicateResourceException("FEATURE_ALREADY_EXISTS",
                    "Feature '" + request.key() + "' already exists");
        }
        Instant now = Instant.now();
        FeatureFlag featureFlag = new FeatureFlag(UUID.randomUUID(), environment.id(), request.key(), request.name(),
                request.description(), request.enabled(), null, 0, now, now);
        try {
            featureFlagRepository.insert(featureFlag);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException("FEATURE_ALREADY_EXISTS",
                    "Feature '" + request.key() + "' already exists");
        }
        featureVersionService.createInitialDraft(featureFlag.id(), request.value(), request.targetingRules());
        return featureFlag;
    }

    @Transactional(readOnly = true)
    public List<FeatureFlag> list(UUID organizationId, String projectKey, String environmentKey) {
        Environment environment = environmentService.get(organizationId, projectKey, environmentKey);
        return featureFlagRepository.findAllByEnvironment(environment.id());
    }

    @Transactional(readOnly = true)
    public FeatureFlag get(UUID organizationId, String projectKey, String environmentKey, String featureKey) {
        return findFeature(organizationId, projectKey, environmentKey, featureKey);
    }

    @Transactional
    public FeatureFlag update(
            UUID organizationId,
            String projectKey,
            String environmentKey,
            String featureKey,
            UpdateFeatureFlagRequest request,
            String correlationId
    ) {
        FeatureFlag featureFlag = findFeature(organizationId, projectKey, environmentKey, featureKey);
        if (request.name() == null && request.description() == null && request.enabled() == null) {
            throw new ConfigurationException("EMPTY_UPDATE", "At least one feature field must be supplied", HttpStatus.BAD_REQUEST);
        }
        boolean enabledChanged = request.enabled() != null && request.enabled() != featureFlag.enabled();
        FeatureFlag updatedFlag = featureFlagRepository.updateMetadata(featureFlag.id(), request.name(), request.description(),
                request.enabled(), Instant.now());

        if (enabledChanged) {
            Project project = projectService.getById(projectService.get(organizationId, projectKey).id());
            Environment environment = environmentService.get(organizationId, projectKey, environmentKey);
            ConfigurationEvent event = ConfigurationEventFactory.enabledOrDisabled(
                    project, environment, updatedFlag, request.enabled(), correlationId);
            eventPublisher.publish(event);
        }

        return updatedFlag;
    }

    public FeatureFlag findFeature(UUID organizationId, String projectKey, String environmentKey, String featureKey) {
        Project project = projectService.get(organizationId, projectKey);
        Environment environment = environmentService.get(organizationId, project.key(), environmentKey);
        return featureFlagRepository.findByEnvironmentAndKey(environment.id(), featureKey)
                .orElseThrow(() -> new ResourceNotFoundException("FEATURE_NOT_FOUND",
                        "Feature '" + featureKey + "' was not found"));
    }
}
