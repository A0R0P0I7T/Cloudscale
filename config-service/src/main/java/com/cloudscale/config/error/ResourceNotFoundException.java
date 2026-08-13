package com.cloudscale.config.error;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ConfigurationException {
    public ResourceNotFoundException(String code, String message) {
        super(code, message, HttpStatus.NOT_FOUND);
    }
}
