package com.cloudscale.evaluation.cache;

import com.cloudscale.evaluation.client.FeatureEvaluationConfiguration;

public record CacheLookup(
        Status status,
        FeatureEvaluationConfiguration configuration
) {
    public enum Status {
        HIT,
        MISS,
        ERROR
    }

    public static CacheLookup hit(FeatureEvaluationConfiguration configuration) {
        return new CacheLookup(Status.HIT, configuration);
    }

    public static CacheLookup miss() {
        return new CacheLookup(Status.MISS, null);
    }

    public static CacheLookup error() {
        return new CacheLookup(Status.ERROR, null);
    }
}
