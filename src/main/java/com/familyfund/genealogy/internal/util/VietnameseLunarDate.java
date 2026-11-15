package com.familyfund.genealogy.internal.util;

import lombok.Builder;

/**
 * Model biểu diễn ngày tháng âm lịch Việt Nam kèm thông tin Can Chi (US-27).
 */
@Builder
public record VietnameseLunarDate(
        int day,
        int month,
        int year,
        boolean leap,
        String canChiYear,
        String formattedAnniversary
) {
    /**
     * Chuỗi đại diện ngày giỗ chuẩn mực trang trọng: "15/02 Giáp Thìn" hoặc "15/02 Nhuận Giáp Thìn".
     */
    public String toShortAnniversaryString() {
        String dayStr = String.format("%02d", day);
        String monthStr = String.format("%02d", month);
        String leapStr = leap ? " Nhuận " : " ";
        return dayStr + "/" + monthStr + leapStr + canChiYear;
    }

    /**
     * Chuỗi đại diện đầy đủ trang trọng: "Ngày 15 tháng 02 năm Giáp Thìn (Âm lịch)".
     */
    public String toFullAnniversaryString() {
        String leapStr = leap ? " (nhuận)" : "";
        return "Ngày " + String.format("%02d", day) + " tháng " + String.format("%02d", month) + leapStr +
                " năm " + canChiYear + " (Âm lịch)";
    }
}
