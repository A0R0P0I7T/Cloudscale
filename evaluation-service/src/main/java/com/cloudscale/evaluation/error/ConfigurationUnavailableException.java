package com.cloudscale.evaluation.error;

import org.springframework.http.HttpStatus;

public class ConfigurationUnavailableException extends EvaluationException {
    public ConfigurationUnavailableException(String message) {
        super("CONFIGURATION_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ConfigurationUnavailableException(String message, Throwable cause) {
        super("CONFIGURATION_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
