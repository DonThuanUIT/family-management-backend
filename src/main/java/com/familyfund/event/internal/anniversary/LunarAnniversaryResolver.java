package com.familyfund.event.internal.anniversary;

import com.familyfund.genealogy.api.dto.LunarDateDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the stored lunar anniversary string and resolves it to a solar date for a given year (US-40).
 *
 * <p>{@code members.death_date_lunar} is written by US-27 as {@code "dd/MM Can Chi"} (or
 * {@code "dd/MM Nhuận Can Chi"} for a leap month), e.g. {@code "15/02 Giáp Thìn"}. This component turns that back
 * into {@code (day, month, leap)} and then walks the solar calendar of one year looking for the solar date whose
 * lunar rendering matches — which is the only correct way to handle a leap month and the 29/30-day month lengths.</p>
 */
@Component
public class LunarAnniversaryResolver {

    /** {@code dd/MM [Nhuận] Can Chi} — the format produced by {@code VietnameseLunarDate.toShortAnniversaryString()}. */
    private static final Pattern ANNIVERSARY_PATTERN =
            Pattern.compile("^\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*(Nhuận|Nhuan)?\\b.*$", Pattern.CASE_INSENSITIVE);

    /** A lunar day/month parsed out of the stored string. */
    public record LunarAnniversary(int day, int month, boolean leap) {}

    /**
     * @param deathDateLunar stored string, e.g. {@code "15/02 Giáp Thìn"}
     * @return the parsed anniversary, or empty when the string is missing or does not start with {@code dd/MM}
     */
    public Optional<LunarAnniversary> parse(String deathDateLunar) {
        // Intentionally NOT cached: the resolver is a stateless component and the pass touches few members.
        if (deathDateLunar == null || deathDateLunar.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = ANNIVERSARY_PATTERN.matcher(deathDateLunar);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int day = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        if (day < 1 || day > 30 || month < 1 || month > 12) {
            return Optional.empty();
        }
        boolean leap = matcher.group(3) != null;
        return Optional.of(new LunarAnniversary(day, month, leap));
    }

    /**
     * Finds the solar date in {@code year} whose lunar date equals the anniversary.
     *
     * <p>Scans the whole solar year once: a lunar date can map to two solar dates only in pathological cases (two
     * leap months), and the first match in chronological order is the anniversary that is actually celebrated.</p>
     *
     * @param anniversary parsed lunar anniversary
     * @param year        solar year to resolve in
     * @param converter   lunar converter (from {@code genealogy::api})
     * @return the solar date of the anniversary in that year, or empty when the year has no such lunar day
     */
    public Optional<LocalDate> resolveSolarDate(LunarAnniversary anniversary, int year,
                                                LunarCalendarPort converter) {
        if (anniversary == null) {
            return Optional.empty();
        }
        LocalDate cursor = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        while (!cursor.isAfter(end)) {
            LunarDateDto lunar = converter.convertSolarToLunar(cursor);
            if (lunar != null
                    && lunar.day() == anniversary.day()
                    && lunar.month() == anniversary.month()
                    && lunar.leap() == anniversary.leap()) {
                return Optional.of(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return Optional.empty();
    }
}
