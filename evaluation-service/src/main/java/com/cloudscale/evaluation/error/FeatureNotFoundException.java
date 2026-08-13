package com.cloudscale.evaluation.error;

import org.springframework.http.HttpStatus;

public class FeatureNotFoundException extends EvaluationException {
    public FeatureNotFoundException(String message) {
        super("FEATURE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
