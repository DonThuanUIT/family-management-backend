package com.familyfund.contribution.internal.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptNumberGeneratorTest {

    private final ReceiptNumberGenerator generator = new ReceiptNumberGenerator();

    @Test
    @DisplayName("Should generate candidate with prefix PT- and length at most 30")
    void shouldGenerateValidReceiptNumber() {
        String receiptNumber = generator.generate();

        assertThat(receiptNumber).isNotNull();
        assertThat(receiptNumber).startsWith(ReceiptNumberGenerator.PREFIX);
        assertThat(receiptNumber.length()).isLessThanOrEqualTo(ReceiptNumberGenerator.MAX_LENGTH);
    }

    @Test
    @DisplayName("Should generate unique receipt numbers across multiple calls")
    void shouldGenerateUniqueReceiptNumbers() {
        Set<String> numbers = new HashSet<>();
        int count = 1000;
        for (int i = 0; i < count; i++) {
            String receiptNumber = generator.generate();
            numbers.add(receiptNumber);
        }
        assertThat(numbers).hasSize(count);
    }
}
