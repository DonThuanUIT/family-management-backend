package com.familyfund.core.audit.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuditSanitizerTest {

    @Test
    @DisplayName("Should mask sensitive keys at top-level")
    void shouldMaskTopLevelSensitiveKeys() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("username", "admin");
        input.put("password", "superSecret123");
        input.put("password_hash", "$2a$10$abcdefghijk");
        input.put("token", "jwt.token.value");
        input.put("apiKey", "xyz-12345");
        input.put("email", "admin@familyfund.vn");

        Map<String, Object> sanitized = AuditSanitizer.sanitize(input);

        assertNotNull(sanitized);
        assertEquals("admin", sanitized.get("username"));
        assertEquals("***", sanitized.get("password"));
        assertEquals("***", sanitized.get("password_hash"));
        assertEquals("***", sanitized.get("token"));
        assertEquals("***", sanitized.get("apiKey"));
        assertEquals("admin@familyfund.vn", sanitized.get("email"));
    }

    @Test
    @DisplayName("Should deeply mask sensitive keys inside nested maps and lists")
    void shouldMaskDeeplyNestedStructures() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("secretKey", "secret-val");
        nested.put("safeField", "ok");

        Map<String, Object> userInList = new LinkedHashMap<>();
        userInList.put("name", "Nguyen Van A");
        userInList.put("credentials", Map.of("password", "123456"));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("config", nested);
        input.put("users", List.of(userInList));

        Map<String, Object> sanitized = AuditSanitizer.sanitize(input);

        assertNotNull(sanitized);
        @SuppressWarnings("unchecked")
        Map<String, Object> resConfig = (Map<String, Object>) sanitized.get("config");
        assertEquals("***", resConfig.get("secretKey"));
        assertEquals("ok", resConfig.get("safeField"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resUsers = (List<Map<String, Object>>) sanitized.get("users");
        assertEquals(1, resUsers.size());
        assertEquals("Nguyen Van A", resUsers.get(0).get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> creds = (Map<String, Object>) resUsers.get(0).get("credentials");
        assertEquals("***", creds.get("password"));
    }

    @Test
    @DisplayName("Should return null or empty when input is null or empty")
    void shouldHandleNullAndEmpty() {
        assertNull(AuditSanitizer.sanitize(null));
        assertTrue(AuditSanitizer.sanitize(Collections.emptyMap()).isEmpty());
    }
}
