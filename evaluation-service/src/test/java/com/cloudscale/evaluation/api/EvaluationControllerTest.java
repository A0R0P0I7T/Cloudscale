package com.cloudscale.evaluation.api;

import com.cloudscale.evaluation.application.EvaluationApplicationService;
import com.cloudscale.evaluation.error.EvaluationExceptionHandler;
import com.cloudscale.evaluation.security.ResourceServerConfiguration;
import com.cloudscale.evaluation.security.TenantAuthorizationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = EvaluationController.class, properties = {
        "cloudscale.jwt.issuer=cloudscale-auth",
        "cloudscale.jwt.secret=evaluation-controller-test-secret-32-bytes"
})
@Import({EvaluationExceptionHandler.class, ResourceServerConfiguration.class, TenantAuthorizationFilter.class})
class EvaluationControllerTest {
    private static final String JWT_SECRET = "evaluation-controller-test-secret-32-bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EvaluationApplicationService evaluationApplicationService;

    @Test
    void rejectsRequestWithoutUserId() throws Exception {
        UUID organizationId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/organizations/{organizationId}/projects/project/environments/staging/features/checkout-v2/evaluate",
                        organizationId)
                        .header("Authorization", "Bearer " + token(organizationId, "ADMIN", "cloudscale-auth"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EvaluationRequest("", java.util.Map.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forwardsBearerTokenAndCorrelationIdToEvaluationService() throws Exception {
        UUID organizationId = UUID.randomUUID();
        String path = "/api/v1/organizations/" + organizationId
                + "/projects/project/environments/staging/features/checkout-v2/evaluate";
        String authorization = "Bearer " + token(organizationId, "DEVELOPER", "cloudscale-auth");
        EvaluationResponse response = new EvaluationResponse("checkout-v2", true, null,
                EvaluationReason.DEFAULT_VALUE, UUID.randomUUID(), 1);
        when(evaluationApplicationService.evaluate(eq(organizationId), eq("project"), eq("staging"),
                eq("checkout-v2"), any(EvaluationRequest.class), eq(authorization), eq("trace-123")))
                .thenReturn(response);

        mockMvc.perform(post(path)
                        .header("Authorization", authorization)
                        .header("X-Correlation-Id", "trace-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EvaluationRequest("user-1", java.util.Map.of()))))
                .andExpect(status().isOk());

        verify(evaluationApplicationService).evaluate(eq(organizationId), eq("project"), eq("staging"),
                eq("checkout-v2"), any(EvaluationRequest.class), eq(authorization), eq("trace-123"));
    }

    @Test
    void rejectsMissingTokenAtTheSecurityChain() throws Exception {
        mockMvc.perform(post("/api/v1/organizations/{organizationId}/projects/project/environments/staging/features/checkout-v2/evaluate",
                        UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EvaluationRequest("user-1", java.util.Map.of()))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(evaluationApplicationService);
    }

    @Test
    void rejectsCrossOrganizationTokenAtTheSecurityChain() throws Exception {
        UUID pathOrganization = UUID.randomUUID();
        String authorization = "Bearer " + token(UUID.randomUUID(), "ADMIN", "cloudscale-auth");

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/projects/project/environments/staging/features/checkout-v2/evaluate",
                        pathOrganization)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EvaluationRequest("user-1", java.util.Map.of()))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(evaluationApplicationService);
    }

    @Test
    void rejectsAValidSignatureWithTheWrongIssuerAtTheSecurityChain() throws Exception {
        UUID organizationId = UUID.randomUUID();
        String authorization = "Bearer " + token(organizationId, "ADMIN", "another-issuer");

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/projects/project/environments/staging/features/checkout-v2/evaluate",
                        organizationId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EvaluationRequest("user-1", java.util.Map.of()))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(evaluationApplicationService);
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
