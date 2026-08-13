package com.cloudscale.evaluation.application;

import com.cloudscale.evaluation.api.EvaluationRequest;
import com.cloudscale.evaluation.client.TargetingRuleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class TargetingRuleMatcher {
    private final ObjectMapper objectMapper;

    public TargetingRuleMatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MatchResult matches(TargetingRuleConfiguration rule, EvaluationRequest request) {
        JsonNode conditions = rule.conditions();
        if (conditions == null || conditions.isNull()) {
            return MatchResult.invalid();
        }

        List<JsonNode> clauses;
        try {
            if (conditions.isObject()) {
                clauses = List.of(conditions);
            } else if (conditions.isArray()) {
                clauses = objectMapper.convertValue(conditions, objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, JsonNode.class));
            } else {
                return MatchResult.invalid();
            }
        } catch (IllegalArgumentException exception) {
            return MatchResult.invalid();
        }

        Map<String, JsonNode> attributes = new HashMap<>();
        attributes.put("userId", objectMapper.valueToTree(request.userId()));
        if (request.attributes() != null) {
            request.attributes().forEach((key, value) -> attributes.put(key, objectMapper.valueToTree(value)));
        }

        for (JsonNode clause : clauses) {
            if (!matchesClause(clause, attributes)) {
                return clauseIsValid(clause) ? MatchResult.noMatch() : MatchResult.invalid();
            }
        }
        return MatchResult.match();
    }

    private boolean matchesClause(JsonNode clause, Map<String, JsonNode> attributes) {
        if (!clauseIsValid(clause)) {
            return false;
        }
        JsonNode candidate = attributes.get(clause.get("attribute").asText());
        if (candidate == null || candidate.isNull()) {
            return false;
        }
        String operator = clause.get("operator").asText();
        JsonNode expected = clause.get("value");
        return switch (operator) {
            case "EQUALS" -> candidate.equals(expected);
            case "NOT_EQUALS" -> !candidate.equals(expected);
            case "IN" -> expected.isArray() && contains(expected, candidate);
            case "NOT_IN" -> expected.isArray() && !contains(expected, candidate);
            default -> false;
        };
    }

    private boolean clauseIsValid(JsonNode clause) {
        if (clause == null || !clause.isObject()) {
            return false;
        }
        JsonNode attribute = clause.get("attribute");
        JsonNode operator = clause.get("operator");
        JsonNode value = clause.get("value");
        if (attribute == null || !attribute.isTextual() || attribute.asText().isBlank()
                || operator == null || !operator.isTextual() || value == null || value.isNull()) {
            return false;
        }
        return switch (operator.asText()) {
            case "EQUALS", "NOT_EQUALS" -> true;
            case "IN", "NOT_IN" -> value.isArray();
            default -> false;
        };
    }

    private boolean contains(JsonNode values, JsonNode candidate) {
        Iterator<JsonNode> iterator = values.elements();
        while (iterator.hasNext()) {
            if (candidate.equals(iterator.next())) {
                return true;
            }
        }
        return false;
    }

    public record MatchResult(boolean valid, boolean matched) {
        static MatchResult match() {
            return new MatchResult(true, true);
        }

        static MatchResult noMatch() {
            return new MatchResult(true, false);
        }

        static MatchResult invalid() {
            return new MatchResult(false, false);
        }
    }
}
