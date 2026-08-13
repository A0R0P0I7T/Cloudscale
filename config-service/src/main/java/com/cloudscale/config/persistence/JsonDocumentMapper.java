package com.cloudscale.config.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonDocumentMapper {
    private final ObjectMapper objectMapper;

    public JsonDocumentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Configuration value could not be serialized", exception);
        }
    }

    public JsonNode read(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored JSON value could not be parsed", exception);
        }
    }
}
