package com.familyfund.genealogy.internal.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchIdGeneratorTest {

    private final BranchIdGenerator generator = new BranchIdGenerator();

    @Test
    @DisplayName("Generated IDs start with BRN and never exceed 20 characters")
    void generatedId_ShouldRespectLengthContract() {
        for (int i = 0; i < 200; i++) {
            String id = generator.generate();
            assertTrue(id.startsWith(BranchIdGenerator.PREFIX), "Expected BRN prefix, got: " + id);
            assertTrue(id.length() <= BranchIdGenerator.MAX_LENGTH,
                    "Expected length <= " + BranchIdGenerator.MAX_LENGTH + ", got: " + id);
            assertEquals(17, id.length(), "Expected exact 17 chars length, got: " + id);
        }
    }

    @Test
    @DisplayName("Generated IDs are unique across a large batch")
    void generatedIds_ShouldBeUnique() {
        Set<String> seen = new HashSet<>();
        int count = 5_000;
        for (int i = 0; i < count; i++) {
            seen.add(generator.generate());
        }
        assertEquals(count, seen.size(), "Expected no duplicates in a batch of " + count);
    }
}
