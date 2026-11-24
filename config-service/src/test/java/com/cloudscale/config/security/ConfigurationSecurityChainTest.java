package com.cloudscale.config.security;

import com.cloudscale.config.application.ProjectService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = com.cloudscale.config.api.ProjectController.class,
        properties = {
                "cloudscale.jwt.issuer=cloudscale-auth",
                "cloudscale.jwt.secret=configuration-security-test-secret-32-bytes"
        })
@Import({ResourceServerConfiguration.class, TenantAuthorizationFilter.class})
class ConfigurationSecurityChainTest {
    private static final String JWT_SECRET = "configuration-security-test-secret-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @Test
    void allowsAValidSameOrganizationReadThroughTheSecurityChain() throws Exception {
        UUID organizationId = UUID.randomUUID();
        when(projectService.list(organizationId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/projects", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(organizationId, "VIEWER", "cloudscale-auth")))
                .andExpect(status().isOk());

        verify(projectService).list(organizationId);
    }

    @Test
    void rejectsMissingAndCrossOrganizationTokensThroughTheSecurityChain() throws Exception {
        UUID organizationId = UUID.randomUUID();
        String path = "/api/v1/organizations/" + organizationId + "/projects";

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(UUID.randomUUID(), "ADMIN", "cloudscale-auth")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(projectService);
    }

    @Test
    void rejectsViewerWritesThroughTheSecurityChain() throws Exception {
        UUID organizationId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/projects", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(organizationId, "VIEWER", "cloudscale-auth"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"web-store","name":"Web store","description":""}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(projectService);
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
