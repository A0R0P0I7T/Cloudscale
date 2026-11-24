package com.cloudscale.evaluation.application;

import com.cloudscale.evaluation.api.EvaluationRequest;
import com.cloudscale.evaluation.api.EvaluationResponse;
import com.cloudscale.evaluation.cache.CacheLookup;
import com.cloudscale.evaluation.cache.EvaluationCache;
import com.cloudscale.evaluation.client.ConfigurationClient;
import com.cloudscale.evaluation.client.FeatureEvaluationConfiguration;
import com.cloudscale.evaluation.metrics.EvaluationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationApplicationServiceTest {
    @Mock
    private EvaluationCache cache;

    @Mock
    private ConfigurationClient configurationClient;

    @Mock
    private FeatureEvaluator featureEvaluator;

    @Test
    void cacheMissFetchesConfigurationAndDoesNotUseUserIdInKey() {
        EvaluationMetrics metrics = new EvaluationMetrics(new SimpleMeterRegistry());
        EvaluationApplicationService service = new EvaluationApplicationService(cache, configurationClient, featureEvaluator, metrics);
        UUID organizationId = UUID.randomUUID();
        EvaluationRequest request = new EvaluationRequest("secret-user", Map.of());
        FeatureEvaluationConfiguration configuration = configuration();
        EvaluationResponse expected = new EvaluationResponse("checkout-v2", true, null,
                com.cloudscale.evaluation.api.EvaluationReason.DEFAULT_VALUE, configuration.publishedVersionId(), 2);
        when(cache.get(any())).thenReturn(CacheLookup.miss());
        when(configurationClient.getEvaluationConfiguration(any(), any(), any(), any(), any(), any()))
                .thenReturn(configuration);
        when(featureEvaluator.evaluate(configuration, request)).thenReturn(expected);

        EvaluationResponse response = service.evaluate(organizationId, "project", "staging", "checkout-v2",
                request, "Bearer user-token", "corr-1");

        assertThat(response).isEqualTo(expected);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(cache).get(key.capture());
        assertThat(key.getValue()).isEqualTo("cloudscale:evaluation-config:" + organizationId + ":project:staging:checkout-v2");
        assertThat(key.getValue()).doesNotContain("secret-user");
        verify(cache).put(key.getValue(), configuration);
        verify(configurationClient).getEvaluationConfiguration(organizationId, "project", "staging", "checkout-v2",
                "Bearer user-token", "corr-1");
    }

    @Test
    void cacheHitDoesNotCallConfigurationService() {
        EvaluationMetrics metrics = new EvaluationMetrics(new SimpleMeterRegistry());
        EvaluationApplicationService service = new EvaluationApplicationService(cache, configurationClient, featureEvaluator, metrics);
        FeatureEvaluationConfiguration configuration = configuration();
        EvaluationRequest request = new EvaluationRequest("user-1", Map.of());
        EvaluationResponse expected = new EvaluationResponse("checkout-v2", true, null,
                com.cloudscale.evaluation.api.EvaluationReason.DEFAULT_VALUE, configuration.publishedVersionId(), 2);
        when(cache.get(any())).thenReturn(CacheLookup.hit(configuration));
        when(featureEvaluator.evaluate(configuration, request)).thenReturn(expected);

        assertThat(service.evaluate(UUID.randomUUID(), "project", "staging", "checkout-v2",
                request, "Bearer user-token", "corr-1"))
                .isEqualTo(expected);

        verify(configurationClient, never()).getEvaluationConfiguration(any(), any(), any(), any(), any(), any());
        verify(cache, never()).put(any(), any());
    }

    private FeatureEvaluationConfiguration configuration() {
        return new FeatureEvaluationConfiguration(UUID.randomUUID(), UUID.randomUUID(), "checkout-v2", true, true,
                UUID.randomUUID(), 2, null, List.of(), 3, null);
    }
}
