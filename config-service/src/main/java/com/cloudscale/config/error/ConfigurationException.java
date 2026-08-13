package com.cloudscale.config.error;

import org.springframework.http.HttpStatus;

public class ConfigurationException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ConfigurationException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
