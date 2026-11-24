package com.cloudscale.config.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAuthorizationPolicyTest {
    private final TenantAuthorizationPolicy policy = new TenantAuthorizationPolicy();
    private final UUID organizationId = UUID.randomUUID();

    @Test
    void rejectsCrossOrganizationRequest() {
        assertThat(policy.isAllowed(organizationId, UUID.randomUUID().toString(), "ADMIN",
                "GET", "/api/v1/organizations/" + organizationId + "/projects")).isFalse();
    }

    @Test
    void viewerCanEvaluateButCannotMutateConfiguration() {
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "VIEWER", "POST",
                "/api/v1/organizations/" + organizationId + "/projects/p/environments/e/features/f/evaluate"))
                .isTrue();
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "VIEWER", "POST",
                "/api/v1/organizations/" + organizationId + "/projects")).isFalse();
    }

    @Test
    void developerCanReadAndWriteWithinTheirOrganization() {
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "DEVELOPER", "GET",
                "/api/v1/organizations/" + organizationId + "/projects")).isTrue();
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "DEVELOPER", "POST",
                "/api/v1/organizations/" + organizationId + "/projects")).isTrue();
    }

    @Test
    void rejectsMalformedOrganizationAndUnknownRoleClaims() {
        assertThat(policy.isAllowed(organizationId, "not-a-uuid", "ADMIN", "GET", "/")).isFalse();
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "OWNER", "GET", "/")).isFalse();
    }
}
