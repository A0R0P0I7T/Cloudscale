package com.cloudscale.auth.api;

import java.time.Instant;

public record AuthErrorResponse(String code, String message, Instant timestamp) {
}
