package com.cloudscale.rollout.domain;

public class InvalidRolloutTransitionException extends RuntimeException {
    public InvalidRolloutTransitionException(String message) {
        super(message);
    }
}
