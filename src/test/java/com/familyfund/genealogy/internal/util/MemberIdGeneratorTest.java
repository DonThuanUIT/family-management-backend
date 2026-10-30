package com.familyfund.genealogy.internal.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the member_id contract used in US-03: MEM prefix, never above 20 characters, no duplicates.
 */
class MemberIdGeneratorTest {

    private final MemberIdGenerator generator = new MemberIdGenerator();

    @Test
    @DisplayName("Generated IDs start with MEM and never exceed 20 characters")
    void generatedId_ShouldRespectLengthContract() {
        for (int i = 0; i < 200; i++) {
            String id = generator.generate();
            assertTrue(id.startsWith(MemberIdGenerator.PREFIX), "Expected MEM prefix, got: " + id);
            assertTrue(id.length() <= MemberIdGenerator.MAX_LENGTH,
                    "Expected length <= " + MemberIdGenerator.MAX_LENGTH + ", got: " + id);
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
