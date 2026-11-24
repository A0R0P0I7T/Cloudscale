package com.cloudscale.evaluation.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAuthorizationPolicyTest {
    private final TenantAuthorizationPolicy policy = new TenantAuthorizationPolicy();
    private final UUID organizationId = UUID.randomUUID();

    @Test
    void rejectsCrossOrganizationRequest() {
        assertThat(policy.isAllowed(organizationId, UUID.randomUUID().toString(), "ADMIN",
                "POST", "/api/v1/organizations/" + organizationId + "/projects/p/evaluations")).isFalse();
    }

    @Test
    void viewerCanEvaluateAndRequestRolloutAssignment() {
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "VIEWER", "POST",
                "/api/v1/organizations/" + organizationId + "/projects/p/environments/e/features/f/evaluate"))
                .isTrue();
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "VIEWER", "POST",
                "/api/v1/organizations/" + organizationId + "/rollouts/assignment")).isTrue();
    }

    @Test
    void viewerCannotChangeConfiguration() {
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "VIEWER", "POST",
                "/api/v1/organizations/" + organizationId + "/projects")).isFalse();
    }
}
