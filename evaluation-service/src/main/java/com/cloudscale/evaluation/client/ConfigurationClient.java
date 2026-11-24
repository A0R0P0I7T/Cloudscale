package com.cloudscale.evaluation.client;

import com.cloudscale.evaluation.error.ConfigurationUnavailableException;
import com.cloudscale.evaluation.error.FeatureNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class ConfigurationClient {
    private final RestClient restClient;

    public ConfigurationClient(
            RestClient.Builder restClientBuilder,
            @Value("${cloudscale.configuration-service.base-url}") String baseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public FeatureEvaluationConfiguration getEvaluationConfiguration(
            UUID organizationId,
            String projectKey,
            String environmentKey,
            String featureKey,
            String authorizationHeader,
            String correlationId
    ) {
        try {
            RestClient.RequestHeadersSpec<?> requestSpec = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/organizations/{organizationId}/projects/{projectKey}/environments/{environmentKey}/features/{featureKey}/evaluation-config")
                            .build(organizationId, projectKey, environmentKey, featureKey))
                    .header("X-Correlation-Id", correlationId);
            if (authorizationHeader != null && authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                requestSpec = requestSpec.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
            }
            return requestSpec.retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        if (response.getStatusCode().value() == 404) {
                            throw new FeatureNotFoundException("Feature '" + featureKey + "' was not found");
                        }
                        throw new ConfigurationUnavailableException("Configuration Service rejected the request");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new ConfigurationUnavailableException("Configuration Service returned an error");
                    })
                    .body(FeatureEvaluationConfiguration.class);
        } catch (FeatureNotFoundException | ConfigurationUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ConfigurationUnavailableException("Configuration Service could not be reached", exception);
        }
    }
}
