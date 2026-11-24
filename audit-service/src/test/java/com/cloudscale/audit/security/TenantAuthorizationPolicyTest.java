package com.cloudscale.audit.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAuthorizationPolicyTest {
    private final TenantAuthorizationPolicy policy = new TenantAuthorizationPolicy();
    private final UUID organizationId = UUID.randomUUID();

    @Test
    void rejectsCrossOrganizationRequest() {
        assertThat(policy.isAllowed(organizationId, UUID.randomUUID().toString(), "VIEWER",
                "GET", "/api/v1/organizations/" + organizationId + "/audit-events")).isFalse();
    }

    @Test
    void everyMemberCanReadAuditButOnlyDevelopersCanMutate() {
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "VIEWER", "GET",
                "/api/v1/organizations/" + organizationId + "/audit-events")).isTrue();
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "VIEWER", "DELETE",
                "/api/v1/organizations/" + organizationId + "/audit-events")).isFalse();
    }

    @Test
    void rejectsInvalidRoleClaim() {
        assertThat(policy.isAllowed(organizationId, organizationId.toString(), "OWNER", "GET",
                "/api/v1/organizations/" + organizationId + "/audit-events")).isFalse();
    }
}
