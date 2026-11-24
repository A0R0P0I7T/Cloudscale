package com.cloudscale.auth.application;

public class InvalidAuthRequestException extends RuntimeException {
    public InvalidAuthRequestException(String message) {
        super(message);
    }
}
