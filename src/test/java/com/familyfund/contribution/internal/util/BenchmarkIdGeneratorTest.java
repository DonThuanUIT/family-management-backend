package com.familyfund.contribution.internal.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkIdGeneratorTest {

    private final BenchmarkIdGenerator generator = new BenchmarkIdGenerator();

    @Test
    @DisplayName("Should generate candidate with prefix BMK and length at most 20")
    void shouldGenerateValidBenchmarkId() {
        String id = generator.generate();

        assertThat(id).isNotNull();
        assertThat(id).startsWith(BenchmarkIdGenerator.PREFIX);
        assertThat(id.length()).isLessThanOrEqualTo(BenchmarkIdGenerator.MAX_LENGTH);
    }

    @Test
    @DisplayName("Should generate unique benchmark IDs across multiple calls")
    void shouldGenerateUniqueIds() {
        Set<String> ids = new HashSet<>();
        int count = 1000;
        for (int i = 0; i < count; i++) {
            String id = generator.generate();
            ids.add(id);
        }
        assertThat(ids).hasSize(count);
    }
}
