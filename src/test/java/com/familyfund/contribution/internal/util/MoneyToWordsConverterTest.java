package com.familyfund.contribution.internal.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyToWordsConverterTest {

    @Test
    @DisplayName("Should convert zero or null to Không đồng")
    void shouldHandleZeroOrNull() {
        assertThat(MoneyToWordsConverter.toVietnameseWords(null)).isEqualTo("Không đồng");
        assertThat(MoneyToWordsConverter.toVietnameseWords(BigDecimal.ZERO)).isEqualTo("Không đồng");
    }

    @Test
    @DisplayName("Should convert round amounts correctly")
    void shouldConvertRoundAmounts() {
        assertThat(MoneyToWordsConverter.toVietnameseWords(BigDecimal.valueOf(500000)))
                .isEqualTo("Năm trăm nghìn đồng chẵn");

        assertThat(MoneyToWordsConverter.toVietnameseWords(BigDecimal.valueOf(1500000)))
                .isEqualTo("Một triệu năm trăm nghìn đồng chẵn");

        assertThat(MoneyToWordsConverter.toVietnameseWords(BigDecimal.valueOf(10000000)))
                .isEqualTo("Mười triệu đồng chẵn");
    }

    @Test
    @DisplayName("Should convert complex amounts with ones and tens correctly")
    void shouldConvertComplexAmounts() {
        assertThat(MoneyToWordsConverter.toVietnameseWords(BigDecimal.valueOf(1050000)))
                .isEqualTo("Một triệu không trăm năm mươi nghìn đồng chẵn");

        assertThat(MoneyToWordsConverter.toVietnameseWords(BigDecimal.valueOf(2521000)))
                .isEqualTo("Hai triệu năm trăm hai mươi mốt nghìn đồng chẵn");
    }
}
