package com.cloudscale.auth.application;

public class AuthConflictException extends RuntimeException {
    public AuthConflictException() {
        super("The organization slug or email is already registered");
    }
}
