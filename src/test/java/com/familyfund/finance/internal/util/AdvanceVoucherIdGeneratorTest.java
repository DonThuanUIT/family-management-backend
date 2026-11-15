package com.familyfund.finance.internal.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdvanceVoucherIdGeneratorTest {

    private final AdvanceVoucherIdGenerator generator = new AdvanceVoucherIdGenerator();

    @Test
    @DisplayName("Generated ID starts with ADV prefix and is <= 20 chars")
    void generatesValidId() {
        String id = generator.generate();
        assertThat(id).isNotNull();
        assertThat(id).startsWith("ADV");
        assertThat(id.length()).isLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("Multiple generated IDs are unique")
    void generatesUniqueIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            assertThat(ids.add(generator.generate())).isTrue();
        }
    }
}
