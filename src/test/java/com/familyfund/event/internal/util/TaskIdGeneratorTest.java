package com.familyfund.event.internal.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TaskIdGenerator Test")
class TaskIdGeneratorTest {

    private static final Pattern TASK_ID_PATTERN = Pattern.compile("^TSK[0-9A-Z]{14}$");

    private TaskIdGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TaskIdGenerator();
    }

    @Test
    @DisplayName("Generated task ID starts with TSK prefix and is at most 20 characters")
    void testBasicFormat() {
        String taskId = generator.generate();
        assertNotNull(taskId);
        assertTrue(taskId.startsWith(TaskIdGenerator.PREFIX));
        assertTrue(taskId.length() <= TaskIdGenerator.MAX_LENGTH);
        assertEquals(17, taskId.length());
        assertTrue(TASK_ID_PATTERN.matcher(taskId).matches(), "Task ID should match pattern: " + taskId);
    }

    @Test
    @DisplayName("Generated task IDs are unique across 10,000 iterations")
    void testUniqueness() {
        Set<String> generatedIds = new HashSet<>();
        int count = 10_000;
        for (int i = 0; i < count; i++) {
            String id = generator.generate();
            assertTrue(generatedIds.add(id), "Duplicate task ID found: " + id);
        }
        assertEquals(count, generatedIds.size());
    }
}

