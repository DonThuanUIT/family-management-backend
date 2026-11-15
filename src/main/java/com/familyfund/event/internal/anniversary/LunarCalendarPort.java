package com.familyfund.event.internal.anniversary;

import com.familyfund.genealogy.api.dto.LunarDateDto;

import java.time.LocalDate;

/**
 * Narrow view of {@link com.familyfund.genealogy.api.LunarCalendarService} used by the anniversary math.
 *
 * <p>A functional interface instead of the full service: {@link LunarAnniversaryResolver} then needs one lambda in
 * its unit tests, with no Spring context and no widening of the genealogy dependency.</p>
 */
@FunctionalInterface
public interface LunarCalendarPort {

    /**
     * @param solarDate solar date
     * @return its Vietnamese lunar date, or {@code null} when the input is {@code null}
     */
    LunarDateDto convertSolarToLunar(LocalDate solarDate);
}
