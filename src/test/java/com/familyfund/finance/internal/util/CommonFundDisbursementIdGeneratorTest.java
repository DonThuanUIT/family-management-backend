package com.familyfund.finance.internal.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CommonFundDisbursementIdGeneratorTest {

    private final CommonFundDisbursementIdGenerator generator = new CommonFundDisbursementIdGenerator();

    @Test
    @DisplayName("Generated disbursement request ID starts with CFD prefix, <= 20 chars and alphanumeric")
    void generatesValidId() {
        String id = generator.generate();
        assertThat(id).isNotNull();
        assertThat(id).startsWith("CFD");
        assertThat(id.length()).isLessThanOrEqualTo(20);
        assertThat(id).matches("^[A-Z0-9]+$");
    }

    @Test
    @DisplayName("Multiple generated disbursement request IDs are unique")
    void generatesUniqueIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(ids.add(generator.generate())).isTrue();
        }
    }
}
