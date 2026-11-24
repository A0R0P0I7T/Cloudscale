package com.cloudscale.audit.api;

import com.cloudscale.audit.application.AuditService;
import com.cloudscale.audit.security.ResourceServerConfiguration;
import com.cloudscale.audit.security.TenantAuthorizationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AuditController.class, properties = {
        "cloudscale.jwt.issuer=cloudscale-auth",
        "cloudscale.jwt.secret=audit-controller-test-secret-at-least-32-bytes"
})
@Import({ResourceServerConfiguration.class, TenantAuthorizationFilter.class})
class AuditControllerTest {
    private static final String JWT_SECRET = "audit-controller-test-secret-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @Test
    void listsEventsForRequestedOrganizationAndPage() throws Exception {
        UUID organizationId = UUID.randomUUID();
        when(auditService.list(organizationId, 2, 25)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/audit-events", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(organizationId, "VIEWER", "cloudscale-auth"))
                        .param("page", "2")
                        .param("size", "25"))
                .andExpect(status().isOk());

        verify(auditService).list(organizationId, 2, 25);
    }

    @Test
    void rejectsPageSizesAboveOneHundred() throws Exception {
        UUID organizationId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/audit-events", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(organizationId, "VIEWER", "cloudscale-auth"))
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingTokenAtTheSecurityChain() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/audit-events", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auditService);
    }

    @Test
    void rejectsCrossOrganizationTokenAtTheSecurityChain() throws Exception {
        UUID pathOrganization = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/audit-events", pathOrganization)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(UUID.randomUUID(), "VIEWER", "cloudscale-auth")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditService);
    }

    @Test
    void rejectsAValidSignatureWithTheWrongIssuerAtTheSecurityChain() throws Exception {
        UUID organizationId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/audit-events", organizationId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(organizationId, "ADMIN", "another-issuer")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auditService);
    }

    private String token(UUID organizationId, String role, String issuer) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .id(UUID.randomUUID().toString())
                .claim("organization_id", organizationId.toString())
                .claim("role", role)
                .build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(
                new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
