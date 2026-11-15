package com.familyfund.genealogy.api.dto;

import lombok.Builder;

/**
 * Read model of a Vietnamese lunar date (US-27 converter, exposed for US-40).
 *
 * <p>Returned by {@link com.familyfund.genealogy.api.LunarCalendarService}; the converter itself lives in
 * {@code genealogy.internal.util} and stays private, so other modules depend on this DTO instead of the
 * implementation.</p>
 *
 * @param day     lunar day of month (1..30)
 * @param month   lunar month (1..12)
 * @param year    lunar year
 * @param leap    {@code true} when this is the leap (nhuận) month of the lunar year
 * @param canChi  Can Chi name of the lunar year, e.g. {@code "Giáp Thìn"}
 */
@Builder
public record LunarDateDto(
        int day,
        int month,
        int year,
        boolean leap,
        String canChi
) {

    /**
     * @return the anniversary form stored in {@code members.death_date_lunar}: {@code "15/02 Giáp Thìn"},
     *         or {@code "15/02 Nhuận Giáp Thìn"} for a leap month.
     */
    public String toAnniversaryString() {
        String leapStr = leap ? " Nhuận " : " ";
        return String.format("%02d/%02d", day, month) + leapStr + canChi;
    }
}
