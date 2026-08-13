package com.cloudscale.config.events;

import com.cloudscale.config.domain.Environment;
import com.cloudscale.config.domain.FeatureFlag;
import com.cloudscale.config.domain.FeatureVersion;
import com.cloudscale.config.domain.Project;
import com.cloudscale.contracts.configuration.ConfigurationEntityType;
import com.cloudscale.contracts.configuration.ConfigurationEvent;
import com.cloudscale.contracts.configuration.ConfigurationEventType;

import java.time.Instant;
import java.util.UUID;

public final class ConfigurationEventFactory {

    private ConfigurationEventFactory() {}

    public static ConfigurationEvent published(
            Project project,
            Environment environment,
            FeatureFlag featureFlag,
            FeatureVersion version,
            String correlationId
    ) {
        return new ConfigurationEvent(
                UUID.randomUUID(),
                ConfigurationEventType.CONFIGURATION_PUBLISHED,
                2,
                Instant.now(),
                project.organizationId(),
                featureFlag.id(),
                ConfigurationEntityType.FEATURE_VERSION,
                "config-service",
                correlationId,
                project.id(),
                project.key(),
                environment.id(),
                environment.key(),
                featureFlag.id(),
                featureFlag.key(),
                version.id(),
                version.versionNumber(),
                featureFlag.revision(),
                featureFlag.enabled()
        );
    }

    public static ConfigurationEvent enabledOrDisabled(
            Project project,
            Environment environment,
            FeatureFlag featureFlag,
            boolean newEnabledState,
            String correlationId
    ) {
        ConfigurationEventType eventType = newEnabledState
                ? ConfigurationEventType.CONFIGURATION_ENABLED
                : ConfigurationEventType.CONFIGURATION_DISABLED;
        return new ConfigurationEvent(
                UUID.randomUUID(),
                eventType,
                2,
                Instant.now(),
                project.organizationId(),
                featureFlag.id(),
                ConfigurationEntityType.FEATURE_FLAG,
                "config-service",
                correlationId,
                project.id(),
                project.key(),
                environment.id(),
                environment.key(),
                featureFlag.id(),
                featureFlag.key(),
                featureFlag.publishedVersionId(),
                null,
                featureFlag.revision(),
                newEnabledState
        );
    }
}
