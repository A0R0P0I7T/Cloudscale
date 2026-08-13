package com.cloudscale.config.error;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends ConfigurationException {
    public InvalidStateTransitionException(String code, String message) {
        super(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
