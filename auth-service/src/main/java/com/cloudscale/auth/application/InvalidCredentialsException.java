package com.cloudscale.auth.application;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Organization, email, or password is invalid");
    }
}
