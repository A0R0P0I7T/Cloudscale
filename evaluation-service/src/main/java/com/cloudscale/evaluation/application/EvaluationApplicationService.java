package com.cloudscale.evaluation.application;

import com.cloudscale.evaluation.api.EvaluationRequest;
import com.cloudscale.evaluation.api.EvaluationResponse;
import com.cloudscale.evaluation.cache.CacheLookup;
import com.cloudscale.evaluation.cache.EvaluationCache;
import com.cloudscale.evaluation.client.ConfigurationClient;
import com.cloudscale.evaluation.client.FeatureEvaluationConfiguration;
import com.cloudscale.evaluation.metrics.EvaluationMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class EvaluationApplicationService {
    private final EvaluationCache evaluationCache;
    private final ConfigurationClient configurationClient;
    private final FeatureEvaluator featureEvaluator;
    private final EvaluationMetrics metrics;

    public EvaluationApplicationService(
            EvaluationCache evaluationCache,
            ConfigurationClient configurationClient,
            FeatureEvaluator featureEvaluator,
            EvaluationMetrics metrics
    ) {
        this.evaluationCache = evaluationCache;
        this.configurationClient = configurationClient;
        this.featureEvaluator = featureEvaluator;
        this.metrics = metrics;
    }

    public EvaluationResponse evaluate(
            UUID organizationId,
            String projectKey,
            String environmentKey,
            String featureKey,
            EvaluationRequest request,
            String authorizationHeader,
            String correlationId
    ) {
        Instant startedAt = Instant.now();
        String cacheKey = cacheKey(organizationId, projectKey, environmentKey, featureKey);
        CacheLookup lookup = evaluationCache.get(cacheKey);
        FeatureEvaluationConfiguration configuration;
        String source;
        if (lookup.status() == CacheLookup.Status.HIT) {
            metrics.cacheHit();
            configuration = lookup.configuration();
            source = "CACHE";
        } else {
            if (lookup.status() == CacheLookup.Status.MISS) {
                metrics.cacheMiss();
            } else {
                metrics.cacheReadError();
            }
            try {
                configuration = configurationClient.getEvaluationConfiguration(
                        organizationId, projectKey, environmentKey, featureKey, authorizationHeader, correlationId);
                metrics.configurationFetch("success");
            } catch (RuntimeException exception) {
                metrics.configurationFetch("error");
                metrics.configurationFetchError();
                throw exception;
            }
            source = "CONFIGURATION_SERVICE";
            if (!evaluationCache.put(cacheKey, configuration)) {
                metrics.cacheWriteError();
            }
        }

        EvaluationResponse response = featureEvaluator.evaluate(configuration, request);
        metrics.evaluationDuration(Duration.between(startedAt, Instant.now()), response.reason(), source);
        return response;
    }

    private String cacheKey(UUID organizationId, String projectKey, String environmentKey, String featureKey) {
        return "cloudscale:evaluation-config:" + organizationId + ":" + projectKey + ":"
                + environmentKey + ":" + featureKey;
    }
}
