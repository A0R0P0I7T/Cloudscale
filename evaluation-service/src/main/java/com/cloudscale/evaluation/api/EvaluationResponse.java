package com.cloudscale.evaluation.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record EvaluationResponse(
        String featureKey,
        boolean enabled,
        JsonNode value,
        EvaluationReason reason,
        UUID publishedVersionId,
        Integer versionNumber
) {
}
