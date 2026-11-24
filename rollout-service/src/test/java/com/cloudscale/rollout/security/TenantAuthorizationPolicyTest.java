package com.cloudscale.rollout.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAuthorizationPolicyTest {
    private final TenantAuthorizationPolicy policy = new TenantAuthorizationPolicy();
    private final UUID organizationId = UUID.randomUUID();

    @Test
    void rejectsCrossOrganizationRequest() {
        assertThat(policy.isAllowed(organizationId, UUID.randomUUID().toString(), "ADMIN",
                "GET", "/api/v1/organizations/" + organizationId + "/rollouts")).isFalse();
    }

    @Test
    void viewerCanRequestAssignmentButCannotChangeRolloutState() {
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "VIEWER", "POST",
                "/api/v1/organizations/" + organizationId + "/rollouts/assignment")).isTrue();
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "VIEWER", "POST",
                "/api/v1/organizations/" + organizationId + "/rollouts")).isFalse();
    }

    @Test
    void developerCanManageRolloutsInTheirOrganization() {
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "DEVELOPER", "POST",
                "/api/v1/organizations/" + organizationId + "/rollouts")).isTrue();
    }
}
