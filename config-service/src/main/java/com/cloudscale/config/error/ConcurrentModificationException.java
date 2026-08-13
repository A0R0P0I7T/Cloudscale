package com.cloudscale.config.error;

import org.springframework.http.HttpStatus;

public class ConcurrentModificationException extends ConfigurationException {
    public ConcurrentModificationException(String message) {
        super("CONCURRENT_MODIFICATION", message, HttpStatus.CONFLICT);
    }
}
