package com.cloudscale.evaluation.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConfigurationClientTest {
    @Test
    void forwardsCallerBearerTokenAndCorrelationId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ConfigurationClient client = new ConfigurationClient(builder, "http://config-service");
        UUID organizationId = UUID.randomUUID();

        server.expect(requestTo("http://config-service/api/v1/organizations/" + organizationId
                        + "/projects/project/environments/staging/features/checkout/evaluation-config"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer verified-user-token"))
                .andExpect(header("X-Correlation-Id", "trace-123"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.getEvaluationConfiguration(organizationId, "project", "staging", "checkout",
                "Bearer verified-user-token", "trace-123");

        server.verify();
    }
}
