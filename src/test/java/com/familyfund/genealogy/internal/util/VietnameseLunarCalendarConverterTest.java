package com.familyfund.genealogy.internal.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("US-27: Vietnamese Lunar Calendar Converter Unit Tests")
class VietnameseLunarCalendarConverterTest {

    private VietnameseLunarCalendarConverter converter;

    @BeforeEach
    void setUp() {
        converter = new VietnameseLunarCalendarConverter();
    }

    @Test
    @DisplayName("Returns null when input date is null")
    void convertSolarToLunar_nullDate_returnsNull() {
        assertNull(converter.convertSolarToLunar(null));
    }

    @ParameterizedTest(name = "Year {0} -> Can Chi {1}")
    @CsvSource({
            "2024, Giáp Thìn",
            "2025, Ất Tỵ",
            "2026, Bính Ngọ",
            "2027, Đinh Mùi",
            "1990, Canh Ngọ",
            "1984, Giáp Tý",
            "1975, Ất Mão",
            "1945, Ất Dậu"
    })
    @DisplayName("Calculates Can Chi of year accurately")
    void getCanChiYear_calculatesCorrectly(int year, String expectedCanChi) {
        assertEquals(expectedCanChi, converter.getCanChiYear(year));
    }

    @Test
    @DisplayName("Converts solar date 2024-03-24 to lunar 15/02 Giáp Thìn (Rằm tháng 2 năm Giáp Thìn)")
    void convertSolarToLunar_standardDate_success() {
        LocalDate solar = LocalDate.of(2024, 3, 24);
        VietnameseLunarDate lunar = converter.convertSolarToLunar(solar);

        assertNotNull(lunar);
        assertEquals(15, lunar.day());
        assertEquals(2, lunar.month());
        assertEquals(2024, lunar.year());
        assertFalse(lunar.leap());
        assertEquals("Giáp Thìn", lunar.canChiYear());
        assertEquals("15/02 Giáp Thìn", lunar.formattedAnniversary());
        assertEquals("Ngày 15 tháng 02 năm Giáp Thìn (Âm lịch)", lunar.toFullAnniversaryString());
    }

    @Test
    @DisplayName("Converts Tet Giap Thin (2024-02-10) to lunar 01/01 Giáp Thìn")
    void convertSolarToLunar_tet2024_success() {
        LocalDate solar = LocalDate.of(2024, 2, 10);
        VietnameseLunarDate lunar = converter.convertSolarToLunar(solar);

        assertNotNull(lunar);
        assertEquals(1, lunar.day());
        assertEquals(1, lunar.month());
        assertEquals(2024, lunar.year());
        assertFalse(lunar.leap());
        assertEquals("Giáp Thìn", lunar.canChiYear());
        assertEquals("01/01 Giáp Thìn", lunar.formattedAnniversary());
    }

    @Test
    @DisplayName("Converts Tet At Ty (2025-01-29) to lunar 01/01 Ất Tỵ")
    void convertSolarToLunar_tet2025_success() {
        LocalDate solar = LocalDate.of(2025, 1, 29);
        VietnameseLunarDate lunar = converter.convertSolarToLunar(solar);

        assertNotNull(lunar);
        assertEquals(1, lunar.day());
        assertEquals(1, lunar.month());
        assertEquals(2025, lunar.year());
        assertEquals("Ất Tỵ", lunar.canChiYear());
        assertEquals("01/01 Ất Tỵ", lunar.formattedAnniversary());
    }
}
