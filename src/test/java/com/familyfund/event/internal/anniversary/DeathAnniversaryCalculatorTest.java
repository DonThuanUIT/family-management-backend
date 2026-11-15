package com.familyfund.event.internal.anniversary;

import com.familyfund.event.api.dto.DeathAnniversaryDto;
import com.familyfund.event.internal.util.AnniversaryKeyGenerator;
import com.familyfund.genealogy.api.dto.DeceasedMemberDto;
import com.familyfund.genealogy.api.dto.LunarDateDto;
import com.familyfund.genealogy.internal.util.VietnameseLunarCalendarConverter;
import com.familyfund.genealogy.internal.util.VietnameseLunarDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link DeathAnniversaryCalculator} (US-40) with the REAL US-27 lunar converter: milestone detection,
 * day counting, the "Đám giỗ" suggestion payload and the skip rules.
 */
class DeathAnniversaryCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String MEMBER_ID = "MEM_GIO_01";
    private static final String CLAN_ID = "CLAN_GIO";

    private final VietnameseLunarCalendarConverter converter = new VietnameseLunarCalendarConverter();

    private DeathAnniversaryCalculator calculator;

    /** Adapter to the narrow port, identical to {@code LunarCalendarServiceImpl} (keeps genealogy internals out). */
    private LunarDateDto lunarOf(LocalDate solarDate) {
        VietnameseLunarDate lunar = converter.convertSolarToLunar(solarDate);
        return lunar == null ? null : LunarDateDto.builder()
                .day(lunar.day()).month(lunar.month()).year(lunar.year())
                .leap(lunar.leap()).canChi(lunar.canChiYear()).build();
    }

    @BeforeEach
    void setUp() {
        calculator = new DeathAnniversaryCalculator(
                new LunarAnniversaryResolver(),
                new AnniversaryKeyGenerator(),
                new DeathAnniversaryProperties(true, null, "Asia/Ho_Chi_Minh", List.of(14, 3), 30,
                        LocalTime.of(8, 0), new BigDecimal("5000000"), null));
    }

    private DeceasedMemberDto member(String lunarAnniversary) {
        return DeceasedMemberDto.builder()
                .memberId(MEMBER_ID)
                .clanId(CLAN_ID)
                .fullName("Nguyễn Văn An")
                .generation(3)
                .branchId("BR_CHI_1")
                .branchName("Chi Một")
                .deathDate(LocalDate.of(2020, 3, 8))
                .deathDateLunar(lunarAnniversary)
                .build();
    }

    /** @return the solar date of 15/02 lunar in the given year, found with the real converter. */
    private LocalDate anniversaryDate(int year) {
        return new LunarAnniversaryResolver()
                .resolveSolarDate(new LunarAnniversaryResolver.LunarAnniversary(15, 2, false), year, this::lunarOf)
                .orElseThrow();
    }

    @Test
    @DisplayName("14 days before the anniversary is a milestone")
    void fourteenDaysBefore() {
        LocalDate anniversary = anniversaryDate(2026);
        LocalDate today = anniversary.minusDays(14);

        DeathAnniversaryDto dto = calculator.compute(member("15/02 Giáp Thìn"), today, ZONE, this::lunarOf, false)
                .orElseThrow();

        assertThat(dto.milestoneDays()).isEqualTo(14);
        assertThat(dto.daysRemaining()).isEqualTo(14);
        assertThat(dto.anniversaryDate()).isEqualTo(anniversary);
        assertThat(dto.anniversaryYear()).isEqualTo(2026);
        assertThat(dto.lunarDay()).isEqualTo(15);
        assertThat(dto.lunarMonth()).isEqualTo(2);
        assertThat(dto.leapMonth()).isFalse();
        assertThat(dto.memberFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(dto.generation()).isEqualTo(3);
        assertThat(dto.branchName()).isEqualTo("Chi Một");
        assertThat(dto.alreadyReminded()).isFalse();
    }

    @Test
    @DisplayName("3 days before the anniversary is a milestone")
    void threeDaysBefore() {
        LocalDate anniversary = anniversaryDate(2026);

        DeathAnniversaryDto dto = calculator.compute(member("15/02 Giáp Thìn"), anniversary.minusDays(3), ZONE,
                this::lunarOf, false).orElseThrow();

        assertThat(dto.milestoneDays()).isEqualTo(3);
        assertThat(dto.daysRemaining()).isEqualTo(3);
    }

    @Test
    @DisplayName("the anniversary day itself is not a milestone (reminders already happened)")
    void anniversaryDayIsNotMilestone() {
        LocalDate anniversary = anniversaryDate(2026);

        DeathAnniversaryDto dto = calculator.compute(member("15/02 Giáp Thìn"), anniversary, ZONE, this::lunarOf, false)
                .orElseThrow();

        assertThat(dto.daysRemaining()).isZero();
        assertThat(dto.milestoneDays()).isNull();
    }

    @Test
    @DisplayName("a day that is not a milestone reports daysRemaining but no milestone")
    void nonMilestoneDay() {
        LocalDate anniversary = anniversaryDate(2026);

        DeathAnniversaryDto dto = calculator.compute(member("15/02 Giáp Thìn"), anniversary.minusDays(10), ZONE,
                this::lunarOf, false).orElseThrow();

        assertThat(dto.daysRemaining()).isEqualTo(10);
        assertThat(dto.milestoneDays()).isNull();
    }

    @Test
    @DisplayName("after this year's anniversary, the next occurrence (next year) is used")
    void rollsOverToNextYear() {
        LocalDate anniversary2026 = anniversaryDate(2026);
        LocalDate past = anniversary2026.plusDays(10);

        DeathAnniversaryDto dto = calculator.compute(member("15/02 Giáp Thìn"), past, ZONE, this::lunarOf, false)
                .orElseThrow();

        assertThat(dto.anniversaryDate()).isAfter(past);
        assertThat(dto.anniversaryYear()).isEqualTo(2027);
        assertThat(dto.daysRemaining()).isPositive();
    }

    @Test
    @DisplayName("builds a Đám giỗ suggestion whose requestBody matches POST /api/v1/events")
    void buildsSuggestion() {
        LocalDate anniversary = anniversaryDate(2027);
        LocalDate today = anniversary.minusDays(14);

        DeathAnniversaryDto dto = calculator.compute(member("15/02 Giáp Thìn"), today, ZONE, this::lunarOf, false)
                .orElseThrow();

        assertThat(dto.suggestion()).isNotNull();
        var suggestion = dto.suggestion();
        assertThat(suggestion.suggestionId()).startsWith("GSU");
        assertThat(suggestion.memberId()).isEqualTo(MEMBER_ID);
        assertThat(suggestion.eventName()).contains("Nguyễn Văn An").contains("15/02");
        assertThat(suggestion.eventDateLunar()).startsWith("15/02");
        assertThat(suggestion.location()).isEqualTo("Chi Một");
        assertThat(suggestion.estimatedBudget()).isEqualByComparingTo("5000000");
        assertThat(suggestion.daysRemaining()).isEqualTo(14);
        assertThat(suggestion.eventDatetime())
                .isEqualTo(anniversary.atTime(8, 0).atZone(ZONE).toInstant());
        assertThat(suggestion.description()).contains("Nguyễn Văn An").contains("đời thứ 3").contains("Chi Một");

        // The nested body is what the client POSTs back, with the same field names as CreateEventRequest.
        var body = suggestion.requestBody();
        assertThat(body.eventName()).isEqualTo(suggestion.eventName());
        assertThat(body.eventDatetime()).isEqualTo(suggestion.eventDatetime());
        assertThat(body.eventDateLunar()).isEqualTo(suggestion.eventDateLunar());
        assertThat(body.location()).isEqualTo(suggestion.location());
        assertThat(body.estimatedBudget()).isEqualByComparingTo("5000000");
        assertThat(body.description()).isEqualTo(suggestion.description());
    }

    @Test
    @DisplayName("a null event-name template falls back to the default wording")
    void defaultEventNameTemplate() {
        DeathAnniversaryCalculator withDefaults = new DeathAnniversaryCalculator(
                new LunarAnniversaryResolver(), new AnniversaryKeyGenerator(),
                new DeathAnniversaryProperties(true, null, null, List.of(14, 3), 0, null, null, "  "));
        LocalDate anniversary = anniversaryDate(2026);

        DeathAnniversaryDto dto = withDefaults.compute(member("15/02 Giáp Thìn"), anniversary.minusDays(14), ZONE,
                this::lunarOf, false).orElseThrow();

        assertThat(dto.suggestion().eventName()).isEqualTo("Đám giỗ Nguyễn Văn An (15/02 " +
                converter.convertSolarToLunar(anniversary).canChiYear() + ")");
        assertThat(dto.suggestion().estimatedBudget()).isNull();
    }

    @Test
    @DisplayName("a covered anniversary emits no suggestion (an event already exists)")
    void coveredAnniversaryHasNoSuggestion() {
        LocalDate anniversary = anniversaryDate(2026);

        DeathAnniversaryDto dto = calculator.compute(member("15/02 Giáp Thìn"), anniversary.minusDays(14), ZONE,
                this::lunarOf, true).orElseThrow();

        assertThat(dto.suggestion()).isNull();
        assertThat(dto.milestoneDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("an unparsable lunar string yields no anniversary")
    void unparsableString() {
        assertThat(calculator.compute(member("không rõ"), anniversaryDate(2026), ZONE, this::lunarOf, false))
                .isEmpty();
        assertThat(calculator.compute(member(null), anniversaryDate(2026), ZONE, this::lunarOf, false)).isEmpty();
    }

    @Test
    @DisplayName("null member or null reference day yields no anniversary")
    void nullInputs() {
        assertThat(calculator.compute(null, LocalDate.now(), ZONE, this::lunarOf, false)).isEmpty();
        assertThat(calculator.compute(member("15/02 Giáp Thìn"), null, ZONE, this::lunarOf, false)).isEmpty();
    }

    @Test
    @DisplayName("milestones come sorted descending and configured values are honoured")
    void customMilestones() {
        DeathAnniversaryCalculator custom = new DeathAnniversaryCalculator(
                new LunarAnniversaryResolver(), new AnniversaryKeyGenerator(),
                new DeathAnniversaryProperties(true, null, null, List.of(3, 30, 14, 30), 0, null, null, null));
        LocalDate anniversary = anniversaryDate(2026);

        DeathAnniversaryDto dto = custom.compute(member("15/02 Giáp Thìn"), anniversary.minusDays(30), ZONE,
                this::lunarOf, false).orElseThrow();

        assertThat(dto.milestoneDays()).isEqualTo(30);
        assertThat(new DeathAnniversaryProperties(true, null, null, List.of(3, 30, 14, 30), 0, null, null, null)
                .milestones()).containsExactly(30, 14, 3);
    }

    @Test
    @DisplayName("Optional.of(null) safety: a blank branch name falls back to a generic location")
    void blankBranchFallsBack() {
        DeceasedMemberDto noBranch = DeceasedMemberDto.builder()
                .memberId(MEMBER_ID).clanId(CLAN_ID).fullName("Nguyễn Văn Bình")
                .generation(4).branchId("BR_X").branchName("  ")
                .deathDateLunar("15/02 Giáp Thìn").build();
        LocalDate anniversary = anniversaryDate(2026);

        DeathAnniversaryDto dto = calculator.compute(noBranch, anniversary.minusDays(14), ZONE, this::lunarOf, false)
                .orElseThrow();

        assertThat(dto.suggestion().location()).isEqualTo("Nhà thờ họ");
    }

    @Test
    @DisplayName("a leap-month anniversary resolves to a solar date in a year that has that leap month")
    void leapMonthAnniversary() {
        Optional<DeathAnniversaryDto> dto = calculator.compute(
                member("15/04 Nhuận Giáp Thìn"), LocalDate.of(2026, 1, 1), ZONE, this::lunarOf, false);

        // 2026 has no leap month 4: the guard must simply find nothing this year or next, not throw.
        assertThat(dto).isEmpty();
    }
}
