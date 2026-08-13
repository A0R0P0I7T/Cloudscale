package com.cloudscale.config.application;

import com.cloudscale.config.api.dto.CreateFeatureVersionRequest;
import com.cloudscale.config.api.dto.PublishedFeatureConfigurationResponse;
import com.cloudscale.config.api.dto.TargetingRuleRequest;
import com.cloudscale.config.domain.Environment;
import com.cloudscale.config.domain.FeatureFlag;
import com.cloudscale.config.domain.FeatureVersion;
import com.cloudscale.config.domain.Project;
import com.cloudscale.config.domain.TargetingRule;
import com.cloudscale.config.domain.VersionStatus;
import com.cloudscale.config.error.ConcurrentModificationException;
import com.cloudscale.config.error.ConfigurationException;
import com.cloudscale.config.error.DuplicateResourceException;
import com.cloudscale.config.error.InvalidStateTransitionException;
import com.cloudscale.config.error.ResourceNotFoundException;
import com.cloudscale.config.events.ConfigurationEventFactory;
import com.cloudscale.config.events.ConfigurationEventPublisher;
import com.cloudscale.config.persistence.EnvironmentRepository;
import com.cloudscale.config.persistence.FeatureFlagRepository;
import com.cloudscale.config.persistence.FeatureVersionRepository;
import com.cloudscale.config.persistence.ProjectRepository;
import com.cloudscale.config.persistence.TargetingRuleRepository;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FeatureVersionService {
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureVersionRepository featureVersionRepository;
    private final TargetingRuleRepository targetingRuleRepository;
    private final ConfigurationEventPublisher eventPublisher;

    public FeatureVersionService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            FeatureFlagRepository featureFlagRepository,
            FeatureVersionRepository featureVersionRepository,
            TargetingRuleRepository targetingRuleRepository,
            ConfigurationEventPublisher eventPublisher
    ) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.featureVersionRepository = featureVersionRepository;
        this.targetingRuleRepository = targetingRuleRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FeatureVersion createInitialDraft(UUID featureFlagId, JsonNode value, List<TargetingRuleRequest> rules) {
        validateConfiguration(value, rules);
        return insertDraft(featureFlagId, value, rules);
    }

    @Transactional
    public FeatureVersion createDraft(
            UUID organizationId,
            String projectKey,
            String environmentKey,
            String featureKey,
            CreateFeatureVersionRequest request
    ) {
        FeatureFlag featureFlag = findFeature(organizationId, projectKey, environmentKey, featureKey);
        validateConfiguration(request.value(), request.targetingRules());
        return insertDraft(featureFlag.id(), request.value(), request.targetingRules());
    }

    @Transactional(readOnly = true)
    public List<FeatureVersion> list(
            UUID organizationId,
            String projectKey,
            String environmentKey,
            String featureKey
    ) {
        FeatureFlag featureFlag = findFeature(organizationId, projectKey, environmentKey, featureKey);
        return featureVersionRepository.findAllByFeature(featureFlag.id());
    }

    @Transactional(readOnly = true)
    public FeatureVersion get(
            UUID organizationId,
            String projectKey,
            String environmentKey,
            String featureKey,
            int versionNumber
    ) {
        FeatureFlag featureFlag = findFeature(organizationId, projectKey, environmentKey, featureKey);
        return featureVersionRepository.findByFeatureAndNumber(featureFlag.id(), versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("FEATURE_VERSION_NOT_FOUND",
                        "Feature version '" + versionNumber + "' was not found"));
    }

    @Transactional
    public FeatureVersion publish(
            UUID organizationId,
            String projectKey,
            String environmentKey,
            String featureKey,
            int versionNumber,
            String correlationId
    ) {
        Project project = projectRepository.findByOrganizationAndKey(organizationId, projectKey)
                .orElseThrow(() -> new ResourceNotFoundException("PROJECT_NOT_FOUND", "Project '" + projectKey + "' was not found"));
        Environment environment = environmentRepository.findByProjectAndKey(project.id(), environmentKey)
                .orElseThrow(() -> new ResourceNotFoundException("ENVIRONMENT_NOT_FOUND",
                        "Environment '" + environmentKey + "' was not found"));
        FeatureFlag requestedFeature = featureFlagRepository.findByEnvironmentAndKey(environment.id(), featureKey)
                .orElseThrow(() -> new ResourceNotFoundException("FEATURE_NOT_FOUND",
                        "Feature '" + featureKey + "' was not found"));

        FeatureFlag featureFlag = featureFlagRepository.lockById(requestedFeature.id())
                .orElseThrow(() -> new ResourceNotFoundException("FEATURE_NOT_FOUND", "Feature was not found"));
        FeatureVersion version = featureVersionRepository.findByFeatureAndNumber(featureFlag.id(), versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("FEATURE_VERSION_NOT_FOUND",
                        "Feature version '" + versionNumber + "' was not found"));
        if (version.status() != VersionStatus.DRAFT) {
            throw new InvalidStateTransitionException("FEATURE_VERSION_NOT_DRAFT",
                    "Only a draft feature version can be published");
        }
        if (featureFlag.publishedVersionId() != null) {
            int archived = featureVersionRepository.updateStatus(featureFlag.publishedVersionId(), VersionStatus.ARCHIVED);
            if (archived != 1) {
                throw new ConcurrentModificationException("The previous published version could not be archived");
            }
        }
        int published = featureVersionRepository.updateStatus(version.id(), VersionStatus.PUBLISHED);
        if (published != 1) {
            throw new ConcurrentModificationException("The feature version could not be published");
        }
        int updated = featureFlagRepository.setPublishedVersion(featureFlag.id(), version.id(), Instant.now());
        if (updated != 1) {
            throw new ConcurrentModificationException("The feature flag could not be updated");
        }

        FeatureVersion publishedVersion = featureVersionRepository.findById(version.id()).orElseThrow();
        FeatureFlag updatedFlag = featureFlagRepository.findById(featureFlag.id()).orElseThrow();

        // Build event inside transaction while DB state is known; publish only after commit
        ConfigurationEvent event = ConfigurationEventFactory.published(
                project, environment, updatedFlag, publishedVersion, correlationId);
        registerAfterCommitPublication(event);

        return publishedVersion;
    }

    @Transactional(readOnly = true)
    public List<TargetingRule> rules(FeatureVersion version) {
        return targetingRuleRepository.findAllByVersion(version.id());
    }

    @Transactional(readOnly = true)
    public PublishedFeatureConfigurationResponse getEvaluationConfiguration(
            UUID organizationId,
            String projectKey,
            String environmentKey,
            String featureKey
    ) {
        FeatureFlag featureFlag = findFeature(organizationId, projectKey, environmentKey, featureKey);
        if (featureFlag.publishedVersionId() == null) {
            return PublishedFeatureConfigurationResponse.from(featureFlag, null, List.of());
        }
        FeatureVersion publishedVersion = featureVersionRepository.findById(featureFlag.publishedVersionId())
                .orElseThrow(() -> new ConfigurationException(
                        "PUBLISHED_CONFIGURATION_UNAVAILABLE",
                        "The published feature version could not be loaded",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        return PublishedFeatureConfigurationResponse.from(featureFlag, publishedVersion,
                targetingRuleRepository.findAllByVersion(publishedVersion.id()));
    }

    private void registerAfterCommitPublication(ConfigurationEvent event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publish(event);
            }
        });
    }

    private FeatureVersion insertDraft(UUID featureFlagId, JsonNode value, List<TargetingRuleRequest> rules) {
        FeatureFlag featureFlag = featureFlagRepository.lockById(featureFlagId)
                .orElseThrow(() -> new ResourceNotFoundException("FEATURE_NOT_FOUND", "Feature was not found"));
        if (featureVersionRepository.findDraft(featureFlag.id()).isPresent()) {
            throw new DuplicateResourceException("DRAFT_ALREADY_EXISTS", "The feature already has a draft version");
        }
        FeatureVersion version = new FeatureVersion(UUID.randomUUID(), featureFlag.id(),
                featureVersionRepository.findNextVersionNumber(featureFlag.id()), VersionStatus.DRAFT,
                value, null, Instant.now());
        featureVersionRepository.insert(version);
        if (rules != null) {
            for (TargetingRuleRequest request : rules) {
                targetingRuleRepository.insert(new TargetingRule(UUID.randomUUID(), version.id(), request.priority(),
                        request.conditions(), request.value(), version.createdAt()));
            }
        }
        return version;
    }

    private FeatureFlag findFeature(UUID organizationId, String projectKey, String environmentKey, String featureKey) {
        Project project = projectRepository.findByOrganizationAndKey(organizationId, projectKey)
                .orElseThrow(() -> new ResourceNotFoundException("PROJECT_NOT_FOUND", "Project '" + projectKey + "' was not found"));
        Environment environment = environmentRepository.findByProjectAndKey(project.id(), environmentKey)
                .orElseThrow(() -> new ResourceNotFoundException("ENVIRONMENT_NOT_FOUND",
                        "Environment '" + environmentKey + "' was not found"));
        return featureFlagRepository.findByEnvironmentAndKey(environment.id(), featureKey)
                .orElseThrow(() -> new ResourceNotFoundException("FEATURE_NOT_FOUND",
                        "Feature '" + featureKey + "' was not found"));
    }

    private void validateConfiguration(JsonNode value, List<TargetingRuleRequest> rules) {
        if (value == null || value.isNull()) {
            throw new ConfigurationException("INVALID_CONFIGURATION_VALUE", "Feature configuration value is required", HttpStatus.BAD_REQUEST);
        }
        if (rules == null) {
            return;
        }
        Set<Integer> priorities = new HashSet<>();
        for (TargetingRuleRequest rule : rules) {
            if (rule.conditions() == null || rule.conditions().isNull()
                    || !(rule.conditions().isObject() || rule.conditions().isArray())) {
                throw new ConfigurationException("INVALID_TARGETING_RULE", "Targeting rule conditions must be a JSON object or array", HttpStatus.BAD_REQUEST);
            }
            if (rule.value() == null || rule.value().isNull()) {
                throw new ConfigurationException("INVALID_TARGETING_RULE", "Targeting rule value is required", HttpStatus.BAD_REQUEST);
            }
            if (!priorities.add(rule.priority())) {
                throw new ConfigurationException("DUPLICATE_TARGETING_PRIORITY", "Targeting rule priorities must be unique", HttpStatus.BAD_REQUEST);
            }
        }
    }
}

