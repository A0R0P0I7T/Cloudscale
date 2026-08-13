package com.cloudscale.evaluation.api;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record EvaluationRequest(
        @NotBlank String userId,
        Map<String, Object> attributes
) {
}
