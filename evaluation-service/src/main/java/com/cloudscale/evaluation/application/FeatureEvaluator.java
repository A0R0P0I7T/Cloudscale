package com.cloudscale.evaluation.application;

import com.cloudscale.evaluation.api.EvaluationReason;
import com.cloudscale.evaluation.api.EvaluationRequest;
import com.cloudscale.evaluation.api.EvaluationResponse;
import com.cloudscale.evaluation.client.FeatureEvaluationConfiguration;
import com.cloudscale.evaluation.client.TargetingRuleConfiguration;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class FeatureEvaluator {
    private final TargetingRuleMatcher targetingRuleMatcher;

    public FeatureEvaluator(TargetingRuleMatcher targetingRuleMatcher) {
        this.targetingRuleMatcher = targetingRuleMatcher;
    }

    public EvaluationResponse evaluate(FeatureEvaluationConfiguration configuration, EvaluationRequest request) {
        if (!configuration.published()) {
            return new EvaluationResponse(configuration.featureKey(), false, null,
                    EvaluationReason.NO_PUBLISHED_CONFIGURATION, null, null);
        }
        if (!configuration.enabled()) {
            return response(configuration, false, EvaluationReason.FEATURE_DISABLED, configuration.value());
        }

        boolean invalidRuleFound = false;
        boolean ruleFound = false;
        for (TargetingRuleConfiguration rule : configuration.safeTargetingRules().stream()
                .sorted(Comparator.comparingInt(TargetingRuleConfiguration::priority)).toList()) {
            TargetingRuleMatcher.MatchResult result = targetingRuleMatcher.matches(rule, request);
            if (!result.valid()) {
                invalidRuleFound = true;
                continue;
            }
            ruleFound = true;
            if (result.matched()) {
                return response(configuration, true, EvaluationReason.TARGETING_MATCH, rule.value());
            }
        }
        if (invalidRuleFound) {
            return response(configuration, true, EvaluationReason.INVALID_TARGETING_RULE, configuration.value());
        }
        if (ruleFound) {
            return response(configuration, true, EvaluationReason.TARGETING_NO_MATCH, configuration.value());
        }
        return response(configuration, true, EvaluationReason.DEFAULT_VALUE, configuration.value());
    }

    private EvaluationResponse response(
            FeatureEvaluationConfiguration configuration,
            boolean enabled,
            EvaluationReason reason,
            com.fasterxml.jackson.databind.JsonNode value
    ) {
        return new EvaluationResponse(configuration.featureKey(), enabled, value, reason,
                configuration.publishedVersionId(), configuration.versionNumber());
    }
}
