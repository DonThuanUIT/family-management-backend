package com.familyfund.genealogy.api;

import com.familyfund.genealogy.api.dto.LunarDateDto;

import java.time.LocalDate;

/**
 * Public contract of the Vietnamese lunar calendar (US-27 algorithm, exposed for US-40).
 *
 * <p>The implementation is the astronomical converter of {@code genealogy.internal.util}; this interface exists so
 * the {@code event} module (daily death-anniversary cron job) can convert dates without reaching into
 * {@code genealogy.internal}.</p>
 */
public interface LunarCalendarService {

    /**
     * Converts a solar date to the Vietnamese lunar calendar (timezone GMT+7).
     *
     * @param solarDate solar date; {@code null} yields {@code null}
     * @return the lunar date, or {@code null} when the input is {@code null}
     */
    LunarDateDto convertSolarToLunar(LocalDate solarDate);

    /**
     * @param lunarYear lunar year (e.g. 2026)
     * @return the Can Chi name of that lunar year, e.g. {@code "Bính Ngọ"}
     */
    String canChiOfYear(int lunarYear);
}
