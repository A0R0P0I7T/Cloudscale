package com.cloudscale.evaluation.application;

import com.cloudscale.evaluation.api.EvaluationReason;
import com.cloudscale.evaluation.api.EvaluationRequest;
import com.cloudscale.evaluation.api.EvaluationResponse;
import com.cloudscale.evaluation.client.FeatureEvaluationConfiguration;
import com.cloudscale.evaluation.client.TargetingRuleConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureEvaluatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private FeatureEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new FeatureEvaluator(new TargetingRuleMatcher(objectMapper));
    }

    @Test
    void returnsDisabledReasonBeforeEvaluatingRules() {
        FeatureEvaluationConfiguration configuration = configuration(false, true, List.of());

        EvaluationResponse response = evaluator.evaluate(configuration, request("pro"));

        assertThat(response.enabled()).isFalse();
        assertThat(response.reason()).isEqualTo(EvaluationReason.FEATURE_DISABLED);
        assertThat(response.value()).isEqualTo(objectMapper.valueToTree(Map.of("variant", "control")));
    }

    @Test
    void returnsNoPublishedConfigurationWhenFeatureHasNoPublishedVersion() {
        FeatureEvaluationConfiguration configuration = configuration(false, false, List.of());

        EvaluationResponse response = evaluator.evaluate(configuration, request("pro"));

        assertThat(response.enabled()).isFalse();
        assertThat(response.value()).isNull();
        assertThat(response.reason()).isEqualTo(EvaluationReason.NO_PUBLISHED_CONFIGURATION);
    }

    @Test
    void returnsFirstMatchingRuleByPriority() {
        TargetingRuleConfiguration lowerPriority = rule(2, "pro", "treatment");
        TargetingRuleConfiguration higherPriority = rule(1, "pro", "preferred");
        FeatureEvaluationConfiguration configuration = configuration(true, true, List.of(lowerPriority, higherPriority));

        EvaluationResponse response = evaluator.evaluate(configuration, request("pro"));

        assertThat(response.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
        assertThat(response.value()).isEqualTo(objectMapper.valueToTree(Map.of("variant", "preferred")));
    }

    @Test
    void requiresAllConditionsInOneRuleToMatch() {
        TargetingRuleConfiguration rule = new TargetingRuleConfiguration(
                UUID.randomUUID(),
                1,
                objectMapper.valueToTree(List.of(
                        Map.of("attribute", "plan", "operator", "EQUALS", "value", "pro"),
                        Map.of("attribute", "region", "operator", "EQUALS", "value", "eu"))),
                objectMapper.valueToTree(Map.of("variant", "treatment")),
                null);

        EvaluationResponse response = evaluator.evaluate(configuration(true, true, List.of(rule)), request("pro"));

        assertThat(response.reason()).isEqualTo(EvaluationReason.TARGETING_NO_MATCH);
        assertThat(response.value()).isEqualTo(objectMapper.valueToTree(Map.of("variant", "control")));
    }

    @Test
    void supportsUserIdAsTargetingAttribute() {
        TargetingRuleConfiguration rule = new TargetingRuleConfiguration(
                UUID.randomUUID(),
                1,
                objectMapper.valueToTree(Map.of("attribute", "userId", "operator", "EQUALS", "value", "user-1")),
                objectMapper.valueToTree(Map.of("variant", "treatment")),
                null);

        EvaluationResponse response = evaluator.evaluate(configuration(true, true, List.of(rule)), request("user-1"));

        assertThat(response.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
    }

    @Test
    void returnsInvalidRuleReasonForUnsupportedOperator() {
        TargetingRuleConfiguration rule = new TargetingRuleConfiguration(
                UUID.randomUUID(),
                1,
                objectMapper.valueToTree(Map.of("attribute", "plan", "operator", "CONTAINS", "value", "pro")),
                objectMapper.valueToTree(Map.of("variant", "treatment")),
                null);

        EvaluationResponse response = evaluator.evaluate(configuration(true, true, List.of(rule)), request("pro"));

        assertThat(response.reason()).isEqualTo(EvaluationReason.INVALID_TARGETING_RULE);
        assertThat(response.value()).isEqualTo(objectMapper.valueToTree(Map.of("variant", "control")));
    }

    @Test
    void supportsInAndNotEqualsOperators() {
        TargetingRuleConfiguration inRule = new TargetingRuleConfiguration(
                UUID.randomUUID(),
                1,
                objectMapper.valueToTree(Map.of("attribute", "plan", "operator", "IN", "value", List.of("pro", "team"))),
                objectMapper.valueToTree(Map.of("variant", "in")),
                null);
        TargetingRuleConfiguration notEqualsRule = new TargetingRuleConfiguration(
                UUID.randomUUID(),
                2,
                objectMapper.valueToTree(Map.of("attribute", "region", "operator", "NOT_EQUALS", "value", "eu")),
                objectMapper.valueToTree(Map.of("variant", "not-eu")),
                null);

        EvaluationResponse response = evaluator.evaluate(
                configuration(true, true, List.of(inRule, notEqualsRule)), request("pro"));

        assertThat(response.reason()).isEqualTo(EvaluationReason.TARGETING_MATCH);
        assertThat(response.value()).isEqualTo(objectMapper.valueToTree(Map.of("variant", "in")));
    }

    private EvaluationRequest request(String plan) {
        return new EvaluationRequest("user-1", Map.of("plan", plan));
    }

    private FeatureEvaluationConfiguration configuration(
            boolean enabled,
            boolean published,
            List<TargetingRuleConfiguration> rules
    ) {
        return new FeatureEvaluationConfiguration(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "checkout-v2",
                enabled,
                published,
                published ? UUID.randomUUID() : null,
                published ? 2 : null,
                published ? objectMapper.valueToTree(Map.of("variant", "control")) : null,
                rules,
                3,
                null);
    }

    private TargetingRuleConfiguration rule(int priority, String plan, String variant) {
        return new TargetingRuleConfiguration(
                UUID.randomUUID(),
                priority,
                objectMapper.valueToTree(Map.of("attribute", "plan", "operator", "EQUALS", "value", plan)),
                objectMapper.valueToTree(Map.of("variant", variant)),
                null);
    }
}
