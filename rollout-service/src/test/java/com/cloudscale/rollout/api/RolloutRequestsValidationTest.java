package com.cloudscale.rollout.api;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RolloutRequestsValidationTest {
    @Test
    void requiresAStageListOnCreate() {
        RolloutRequests.CreateRolloutRequest request = new RolloutRequests.CreateRolloutRequest(
                UUID.randomUUID(), UUID.randomUUID(), null, null);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(request);

            assertThat(violations)
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("stages");
        }
    }
}
