package com.cloudscale.rollout.application;

public class RolloutNotFoundException extends RuntimeException {
    public RolloutNotFoundException() {
        super("Rollout was not found");
    }
}
