package com.familyfund.event.internal.anniversary;

import com.familyfund.genealogy.api.dto.LunarDateDto;
import com.familyfund.genealogy.internal.util.VietnameseLunarCalendarConverter;
import com.familyfund.genealogy.internal.util.VietnameseLunarDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link LunarAnniversaryResolver} (US-40): parsing the stored {@code "dd/MM Can Chi"} string and
 * resolving it back to a solar date with the real US-27 converter.
 */
class LunarAnniversaryResolverTest {

    private final LunarAnniversaryResolver resolver = new LunarAnniversaryResolver();
    private final VietnameseLunarCalendarConverter converter = new VietnameseLunarCalendarConverter();

    /** Adapter to the narrow port the resolver consumes (same mapping as {@code LunarCalendarServiceImpl}). */
    private LunarDateDto lunarOf(java.time.LocalDate solarDate) {
        VietnameseLunarDate lunar = converter.convertSolarToLunar(solarDate);
        return lunar == null ? null : LunarDateDto.builder()
                .day(lunar.day()).month(lunar.month()).year(lunar.year())
                .leap(lunar.leap()).canChi(lunar.canChiYear()).build();
    }

    @Test
    @DisplayName("parses the canonical anniversary string written by US-27")
    void parsesCanonicalString() {
        Optional<LunarAnniversaryResolver.LunarAnniversary> parsed = resolver.parse("15/02 Giáp Thìn");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().day()).isEqualTo(15);
        assertThat(parsed.get().month()).isEqualTo(2);
        assertThat(parsed.get().leap()).isFalse();
    }

    @Test
    @DisplayName("parses a leap-month anniversary")
    void parsesLeapMonth() {
        Optional<LunarAnniversaryResolver.LunarAnniversary> parsed = resolver.parse("15/02 Nhuận Giáp Thìn");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().leap()).isTrue();
    }

    @Test
    @DisplayName("tolerates padding, spacing and a missing Can Chi")
    void parsesLooseFormats() {
        assertThat(resolver.parse("5/2")).isPresent();
        assertThat(resolver.parse("  05 / 02  ")).isPresent();
        assertThat(resolver.parse("05/02")).map(LunarAnniversaryResolver.LunarAnniversary::day).hasValue(5);
    }

    @Test
    @DisplayName("rejects null, blank and unparsable strings")
    void rejectsInvalidStrings() {
        assertThat(resolver.parse(null)).isEmpty();
        assertThat(resolver.parse("   ")).isEmpty();
        assertThat(resolver.parse("ngày 15 tháng 2")).isEmpty();
    }

    @Test
    @DisplayName("rejects an out-of-range day or month")
    void rejectsOutOfRange() {
        assertThat(resolver.parse("31/02 Giáp Thìn")).isEmpty();
        assertThat(resolver.parse("15/13 Giáp Thìn")).isEmpty();
        assertThat(resolver.parse("00/02 Giáp Thìn")).isEmpty();
    }

    @Test
    @DisplayName("resolves the anniversary to a solar date in a given year")
    void resolvesSolarDate() {
        Optional<LocalDate> resolved = resolver.resolveSolarDate(
                new LunarAnniversaryResolver.LunarAnniversary(15, 2, false), 2026, this::lunarOf);

        assertThat(resolved).isPresent();
        // Invariants rather than a hardcoded calendar: the resolved day really is 15/02 lunar, and it sits in the
        // first half of 2026 (lunar month 2 always falls in Feb-Apr).
        assertThat(lunarOf(resolved.get()).day()).isEqualTo(15);
        assertThat(lunarOf(resolved.get()).month()).isEqualTo(2);
        assertThat(resolved.get().getYear()).isEqualTo(2026);
        assertThat(resolved.get().getMonthValue()).isBetween(2, 4);
    }

    @Test
    @DisplayName("resolved date really renders back to the same lunar day and month")
    void resolvedDateRoundTrips() {
        Optional<LocalDate> resolved = resolver.resolveSolarDate(
                new LunarAnniversaryResolver.LunarAnniversary(10, 3, false), 2027, this::lunarOf);

        assertThat(resolved).isPresent();
        var lunar = converter.convertSolarToLunar(resolved.get());
        assertThat(lunar.day()).isEqualTo(10);
        assertThat(lunar.month()).isEqualTo(3);
    }

    @Test
    @DisplayName("returns empty when the year has no such lunar day")
    void returnsEmptyWhenUnresolvable() {
        Optional<LocalDate> resolved = resolver.resolveSolarDate(
                new LunarAnniversaryResolver.LunarAnniversary(30, 2, true), 2026, this::lunarOf);

        // 2026 has no leap month 2 at all, so nothing matches.
        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("a null anniversary resolves to empty")
    void nullAnniversary() {
        assertThat(resolver.resolveSolarDate(null, 2026, this::lunarOf)).isEmpty();
    }
}
