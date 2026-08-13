package com.cloudscale.evaluation.api;

import com.cloudscale.evaluation.application.EvaluationApplicationService;
import com.cloudscale.evaluation.error.EvaluationExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvaluationController.class)
@Import(EvaluationExceptionHandler.class)
class EvaluationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EvaluationApplicationService evaluationApplicationService;

    @Test
    void rejectsRequestWithoutUserId() throws Exception {
        mockMvc.perform(post("/api/v1/organizations/{organizationId}/projects/project/environments/staging/features/checkout-v2/evaluate",
                        UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EvaluationRequest("", java.util.Map.of()))))
                .andExpect(status().isBadRequest());
    }
}
