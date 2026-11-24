package com.cloudscale.evaluation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TenantAuthorizationFilter extends OncePerRequestFilter {
    private static final String ORGANIZATION_PREFIX = "/api/v1/organizations/";
    private final TenantAuthorizationPolicy policy = new TenantAuthorizationPolicy();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ORGANIZATION_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        UUID organizationId = pathOrganizationId(request.getRequestURI());
        if (organizationId == null || !policy.isAllowed(organizationId,
                jwtAuthentication.getToken().getClaimAsString("organization_id"),
                jwtAuthentication.getToken().getClaimAsString("role"),
                request.getMethod(), request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"ACCESS_DENIED\",\"message\":\"The authenticated member is not authorized for this organization\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private UUID pathOrganizationId(String path) {
        String[] parts = path.split("/");
        if (parts.length < 5 || !"organizations".equals(parts[3])) {
            return null;
        }
        try {
            return UUID.fromString(parts[4]);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
