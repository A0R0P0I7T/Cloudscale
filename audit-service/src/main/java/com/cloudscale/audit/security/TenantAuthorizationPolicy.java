package com.cloudscale.audit.security;

import java.util.Set;
import java.util.UUID;

public final class TenantAuthorizationPolicy {
    private static final Set<String> ROLES = Set.of("ADMIN", "DEVELOPER", "VIEWER");
    private static final Set<String> WRITER_ROLES = Set.of("ADMIN", "DEVELOPER");

    public boolean isAllowed(UUID requestOrganizationId, String organizationClaim, String role, String method, String path) {
        if (requestOrganizationId == null || organizationClaim == null || role == null || method == null || path == null) {
            return false;
        }
        try {
            if (!requestOrganizationId.equals(UUID.fromString(organizationClaim))) {
                return false;
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!ROLES.contains(role)) {
            return false;
        }
        if (Set.of("GET", "HEAD", "OPTIONS").contains(method)) {
            return true;
        }
        if ("POST".equals(method) && (isEvaluationPath(path) || isRolloutAssignmentPath(path))) {
            return true;
        }
        return WRITER_ROLES.contains(role);
    }

    private boolean isEvaluationPath(String path) {
        return path.matches("/api/v1/organizations/[^/]+/projects/[^/]+/environments/[^/]+/features/[^/]+/evaluate");
    }

    private boolean isRolloutAssignmentPath(String path) {
        return path.matches("/api/v1/organizations/[^/]+/rollouts/assignment");
    }
}
