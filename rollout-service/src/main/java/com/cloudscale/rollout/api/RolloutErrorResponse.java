package com.cloudscale.rollout.api;

import java.time.Instant;

public record RolloutErrorResponse(String code, String message, Instant timestamp) {
}
