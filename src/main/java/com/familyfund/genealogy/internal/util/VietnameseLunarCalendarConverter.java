package com.familyfund.genealogy.internal.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Bộ chuyển đổi Dương lịch sang Âm lịch Việt Nam chuẩn múi giờ GMT+7 (US-27).
 * <p>
 * Dựa trên thuật toán thiên văn Hồ Ngọc Đức (Viện Công nghệ Thông tin),
 * sử dụng công thức tính Sóc (New Moon) và Tiết Khí (Solar Terms) theo Jean Meeus.
 * Độ chính xác cao cho các năm từ 1800 đến 2199.
 */
@Component
public class VietnameseLunarCalendarConverter {

    public static final double VIETNAM_TIMEZONE = 7.0;

    private static final String[] CAN = {
            "Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"
    };

    private static final String[] CHI = {
            "Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"
    };

    /**
     * Chuyển đổi một ngày Dương lịch ({@link LocalDate}) sang ngày Âm lịch Việt Nam.
     *
     * @param solarDate ngày dương lịch cần chuyển
     * @return đối tượng {@link VietnameseLunarDate} chứa ngày, tháng, năm âm lịch và Can Chi
     */
    public VietnameseLunarDate convertSolarToLunar(LocalDate solarDate) {
        if (solarDate == null) {
            return null;
        }
        return convertSolarToLunar(solarDate.getDayOfMonth(), solarDate.getMonthValue(), solarDate.getYear());
    }

    /**
     * Chuyển đổi ngày, tháng, năm Dương lịch sang Âm lịch Việt Nam.
     */
    public VietnameseLunarDate convertSolarToLunar(int sDay, int sMonth, int sYear) {
        int dayNumber = jdFromDate(sDay, sMonth, sYear);
        int k = (int) Math.floor((dayNumber - 2415021.076998695) / 29.530588853);
        int monthStart = getNewMoonDay(k + 1, VIETNAM_TIMEZONE);
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k, VIETNAM_TIMEZONE);
        } else {
            k++;
        }

        // Tính tháng 11 âm lịch của năm trước hoặc năm nay
        int a11 = getLunarMonth11(sYear, VIETNAM_TIMEZONE);
        int b11 = a11;
        int lYear;
        if (a11 >= monthStart) {
            lYear = sYear;
            a11 = getLunarMonth11(sYear - 1, VIETNAM_TIMEZONE);
        } else {
            lYear = sYear + 1;
            b11 = getLunarMonth11(sYear + 1, VIETNAM_TIMEZONE);
        }

        int lDay = dayNumber - monthStart + 1;
        int diff = (int) Math.floor((monthStart - a11) / 29.0);
        int lMonth = diff + 11;
        boolean isLeap = false;

        if (b11 - a11 > 365) {
            int leapMonthDiff = getLeapMonthOffset(a11, VIETNAM_TIMEZONE);
            if (diff >= leapMonthDiff) {
                lMonth = diff + 10;
                if (diff == leapMonthDiff) {
                    isLeap = true;
                }
            }
        }

        if (lMonth > 12) {
            lMonth -= 12;
        }
        if (lMonth >= 11 && diff < 4) {
            lYear--;
        }

        String canChi = getCanChiYear(lYear);

        VietnameseLunarDate lunarDate = VietnameseLunarDate.builder()
                .day(lDay)
                .month(lMonth)
                .year(lYear)
                .leap(isLeap)
                .canChiYear(canChi)
                .build();

        return VietnameseLunarDate.builder()
                .day(lDay)
                .month(lMonth)
                .year(lYear)
                .leap(isLeap)
                .canChiYear(canChi)
                .formattedAnniversary(lunarDate.toShortAnniversaryString())
                .build();
    }

    /**
     * Tính Can Chi của năm âm lịch (ví dụ: 2024 -> Giáp Thìn).
     */
    public String getCanChiYear(int lunarYear) {
        int canIndex = (lunarYear + 6) % 10;
        if (canIndex < 0) canIndex += 10;

        int chiIndex = (lunarYear + 8) % 12;
        if (chiIndex < 0) chiIndex += 12;

        return CAN[canIndex] + " " + CHI[chiIndex];
    }

    // ==================== CÁC HÀM THIÊN VĂN BỔ TRỢ ====================

    private static int jdFromDate(int dd, int mm, int yy) {
        int a = (14 - mm) / 12;
        int y = yy + 4800 - a;
        int m = mm + 12 * a - 3;
        int jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        }
        return jd;
    }

    private static int getNewMoonDay(int k, double timeZone) {
        double t = k / 1236.85;
        double t2 = t * t;
        double t3 = t2 * t;
        double dr = Math.PI / 180;

        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3;
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr);

        // Mặt trời
        double m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3;
        // Mặt trăng
        double mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3;
        // Khoảng cách góc
        double f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3;

        double c1 = (0.1734 - 0.000393 * t) * Math.sin(m * dr) + 0.0021 * Math.sin(2 * m * dr);
        c1 -= 0.4068 * Math.sin(mpr * dr) + 0.0161 * Math.sin(2 * mpr * dr);
        c1 -= 0.0004 * Math.sin(3 * mpr * dr);
        c1 += 0.0104 * Math.sin(2 * f * dr) - 0.0051 * Math.sin((m + mpr) * dr);
        c1 -= 0.0074 * Math.sin((m - mpr) * dr) + 0.0004 * Math.sin((2 * f + m) * dr);
        c1 -= 0.0004 * Math.sin((2 * f - m) * dr) - 0.0006 * Math.sin((2 * f + mpr) * dr);
        c1 += 0.0010 * Math.sin((2 * f - mpr) * dr) + 0.0005 * Math.sin((m + 2 * mpr) * dr);

        double jd = jd1 + c1;
        return (int) Math.floor(jd + 0.5 + timeZone / 24.0);
    }

    private static double getSunLongitude(int dayNumber, double timeZone) {
        double t = (dayNumber - 0.5 - timeZone / 24.0 - 2451545.0) / 36525.0;
        double t2 = t * t;
        double dr = Math.PI / 180;
        double l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2;
        double m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2;
        double c = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(m * dr);
        c += (0.019993 - 0.000101 * t) * Math.sin(2 * m * dr);
        c += 0.000290 * Math.sin(3 * m * dr);
        double theta = l0 + c;
        double lambda = theta - 0.00569 - 0.00478 * Math.sin((125.04 - 1934.136 * t) * dr);
        lambda = lambda * dr;
        lambda = lambda - Math.PI * 2 * Math.floor(lambda / (Math.PI * 2));
        return Math.floor(lambda / Math.PI * 6);
    }

    private static int getLunarMonth11(int yy, double timeZone) {
        int off = jdFromDate(31, 12, yy) - 2415021;
        int k = (int) Math.floor(off / 29.530588853);
        int nm = getNewMoonDay(k, timeZone);
        double sunLong = getSunLongitude(nm, timeZone);
        if (sunLong >= 9) {
            nm = getNewMoonDay(k - 1, timeZone);
        }
        return nm;
    }

    private static int getLeapMonthOffset(int a11, double timeZone) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int last = 0;
        int i = 1;
        double arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
        do {
            last = (int) arc;
            i++;
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone);
        } while (arc != last && i < 14);
        return i - 1;
    }
}
