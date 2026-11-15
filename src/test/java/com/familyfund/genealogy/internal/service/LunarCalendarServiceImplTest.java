package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.api.dto.LunarDateDto;
import com.familyfund.genealogy.internal.util.VietnameseLunarCalendarConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link LunarCalendarServiceImpl} (US-40): the api port must behave exactly like the US-27 converter it
 * delegates to, including the null and Can Chi handling.
 */
class LunarCalendarServiceImplTest {

    private final VietnameseLunarCalendarConverter converter = new VietnameseLunarCalendarConverter();
    private final LunarCalendarServiceImpl service = new LunarCalendarServiceImpl(converter);

    @Test
    @DisplayName("converts a solar date through the US-27 algorithm")
    void convertsSolarDate() {
        LocalDate solar = LocalDate.of(2026, 4, 2);

        LunarDateDto lunar = service.convertSolarToLunar(solar);

        assertThat(lunar).isNotNull();
        assertThat(lunar.day()).isEqualTo(15);
        assertThat(lunar.month()).isEqualTo(2);
        assertThat(lunar.year()).isEqualTo(2026);
        assertThat(lunar.leap()).isFalse();
        assertThat(lunar.canChi()).isEqualTo("Bính Ngọ");
    }

    @Test
    @DisplayName("null in, null out")
    void nullSafe() {
        assertThat(service.convertSolarToLunar(null)).isNull();
    }

    @Test
    @DisplayName("Can Chi of a lunar year")
    void canChi() {
        assertThat(service.canChiOfYear(2026)).isEqualTo("Bính Ngọ");
        assertThat(service.canChiOfYear(2025)).isEqualTo("Ất Tỵ");
        assertThat(service.canChiOfYear(2024)).isEqualTo("Giáp Thìn");
    }

    @Test
    @DisplayName("the DTO renders the anniversary labels used in notifications")
    void dtoLabels() {
        LunarDateDto lunar = service.convertSolarToLunar(LocalDate.of(2026, 4, 2));

        assertThat(lunar.toAnniversaryString()).startsWith("15/02 ").endsWith("Bính Ngọ");
    }
}
