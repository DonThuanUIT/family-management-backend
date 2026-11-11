package com.familyfund.event.internal.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AlertIdGenerator Test")
class AlertIdGeneratorTest {

    private static final Pattern ALERT_ID_PATTERN = Pattern.compile("^PWA[0-9A-Z]{14}$");

    private AlertIdGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new AlertIdGenerator();
    }

    @Test
    @DisplayName("Generated alert ID starts with PWA prefix and is at most 20 characters")
    void testBasicFormat() {
        String alertId = generator.generate();
        assertNotNull(alertId);
        assertTrue(alertId.startsWith(AlertIdGenerator.PREFIX));
        assertTrue(alertId.length() <= AlertIdGenerator.MAX_LENGTH);
        assertEquals(17, alertId.length());
        assertTrue(ALERT_ID_PATTERN.matcher(alertId).matches(), "Alert ID should match pattern: " + alertId);
    }

    @Test
    @DisplayName("Generated alert IDs are unique across 10,000 iterations")
    void testUniqueness() {
        Set<String> generatedIds = new HashSet<>();
        int count = 10_000;
        for (int i = 0; i < count; i++) {
            String id = generator.generate();
            assertTrue(generatedIds.add(id), "Duplicate alert ID found: " + id);
        }
        assertEquals(count, generatedIds.size());
    }
}