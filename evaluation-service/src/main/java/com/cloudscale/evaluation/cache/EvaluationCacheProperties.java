package com.cloudscale.evaluation.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudscale.evaluation.cache")
public record EvaluationCacheProperties(long ttlSeconds) {
    public EvaluationCacheProperties {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("Evaluation cache TTL must be positive");
        }
    }
}
