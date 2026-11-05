package com.familyfund.audit.util;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Sanitizes old/new JSON snapshots to ensure sensitive data (passwords, tokens, secrets)
 * is never persisted to the audit log.
 */
@Component
public class AuditSanitizer {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "passwordhash",
            "password_hash",
            "token",
            "refreshtoken",
            "refresh_token",
            "secret",
            "authorization",
            "apikey",
            "api_key"
    );

    private static final String MASKED_VALUE = "***";

    public static Map<String, Object> sanitize(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return null;
        }
        return sanitizeMap(snapshot);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sanitizeMap(Map<String, Object> input) {
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveKey(key)) {
                clean.put(key, MASKED_VALUE);
            } else if (value instanceof Map<?, ?> nestedMap) {
                clean.put(key, sanitizeMap((Map<String, Object>) nestedMap));
            } else if (value instanceof List<?> list) {
                clean.put(key, sanitizeList(list));
            } else {
                clean.put(key, value);
            }
        }
        return clean;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> sanitizeList(List<?> input) {
        List<Object> clean = new ArrayList<>();
        for (Object item : input) {
            if (item instanceof Map<?, ?> nestedMap) {
                clean.add(sanitizeMap((Map<String, Object>) nestedMap));
            } else if (item instanceof List<?> nestedList) {
                clean.add(sanitizeList(nestedList));
            } else {
                clean.add(item);
            }
        }
        return clean;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[-_]", "");
        for (String sensitive : SENSITIVE_KEYS) {
            String normSens = sensitive.replaceAll("[-_]", "");
            if (normalized.contains(normSens)) {
                return true;
            }
        }
        return false;
    }
}
