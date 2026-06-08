package com.tradingbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonUtil {

    private final ObjectMapper objectMapper;

    public <T> Optional<T> parse(String json, Class<T> clazz) {
        try {
            return Optional.of(objectMapper.readValue(json, clazz));
        } catch (Exception e) {
            log.debug("JSON parse failed for class {}: {}", clazz.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    public <T> Optional<T> parse(String json, TypeReference<T> typeRef) {
        try {
            return Optional.of(objectMapper.readValue(json, typeRef));
        } catch (Exception e) {
            log.debug("JSON parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> toJson(Object obj) {
        try {
            return Optional.of(objectMapper.writeValueAsString(obj));
        } catch (Exception e) {
            log.debug("JSON serialization failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<String> extractJsonBlock(String text) {
        if (text == null) return Optional.empty();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String candidate = text.substring(start, end + 1);
            if (isValidJson(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
