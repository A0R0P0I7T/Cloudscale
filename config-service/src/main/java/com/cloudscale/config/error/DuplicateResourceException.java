package com.cloudscale.config.error;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ConfigurationException {
    public DuplicateResourceException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
}
