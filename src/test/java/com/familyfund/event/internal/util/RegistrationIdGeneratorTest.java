package com.familyfund.event.internal.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RegistrationIdGenerator Test")
class RegistrationIdGeneratorTest {

    private static final Pattern REGISTRATION_ID_PATTERN = Pattern.compile("^REG[0-9A-Z]{14}$");

    private RegistrationIdGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new RegistrationIdGenerator();
    }

    @Test
    @DisplayName("Generated registration ID starts with REG prefix and is exactly 17 characters (<= 20)")
    void testBasicFormat() {
        String regId = generator.generate();
        assertNotNull(regId);
        assertTrue(regId.startsWith(RegistrationIdGenerator.PREFIX));
        assertTrue(regId.length() <= RegistrationIdGenerator.MAX_LENGTH);
        assertEquals(17, regId.length());
        assertTrue(REGISTRATION_ID_PATTERN.matcher(regId).matches(),
                "Registration ID should match pattern: " + regId);
    }

    @Test
    @DisplayName("Generated registration IDs are unique across 10,000 iterations")
    void testUniqueness() {
        Set<String> generatedIds = new HashSet<>();
        int count = 10_000;
        for (int i = 0; i < count; i++) {
            String id = generator.generate();
            assertTrue(generatedIds.add(id), "Duplicate registration ID found: " + id);
        }
        assertEquals(count, generatedIds.size());
    }
}

