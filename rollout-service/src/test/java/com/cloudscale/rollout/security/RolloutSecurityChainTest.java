package com.cloudscale.rollout.security;

import com.cloudscale.rollout.application.RolloutApplicationService;
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
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = com.cloudscale.rollout.api.RolloutController.class,
        properties = {
                "cloudscale.jwt.issuer=cloudscale-auth",
                "cloudscale.jwt.secret=rollout-security-test-secret-at-least-32-bytes"
        })
@Import({ResourceServerConfiguration.class, TenantAuthorizationFilter.class})
class RolloutSecurityChainTest {
    private static final String JWT_SECRET = "rollout-security-test-secret-at-least-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RolloutApplicationService rolloutService;

    @Test
    void rejectsMissingAndCrossOrganizationTokensThroughTheSecurityChain() throws Exception {
        UUID organizationId = UUID.randomUUID();
        String path = "/api/v1/organizations/" + organizationId + "/rollouts";

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(UUID.randomUUID(), "ADMIN", "cloudscale-auth")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rolloutService);
    }

    @Test
    void rejectsViewerWritesThroughTheSecurityChain() throws Exception {
        UUID organizationId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/rollouts", organizationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(organizationId, "VIEWER", "cloudscale-auth"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"featureFlagId":"b2bb1375-a6d7-458d-a732-4ccab7a9df04",
                                 "environmentId":"065d8c23-5b9b-40f1-bfce-ef541f2387d2",
                                 "stages":[{"targetPercentage":100,"durationMinutes":1}]}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rolloutService);
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
