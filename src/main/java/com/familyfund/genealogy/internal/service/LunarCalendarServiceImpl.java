package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.api.LunarCalendarService;
import com.familyfund.genealogy.api.dto.LunarDateDto;
import com.familyfund.genealogy.internal.util.VietnameseLunarCalendarConverter;
import com.familyfund.genealogy.internal.util.VietnameseLunarDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Exposes the US-27 converter through the public {@link LunarCalendarService} port.
 *
 * <p>Thin on purpose: the algorithm stays where US-27 put it ({@code VietnameseLunarCalendarConverter}) and this
 * adapter only maps {@code VietnameseLunarDate} to the api DTO, so US-40 can convert dates from another module
 * without duplicating astronomy code or leaking {@code genealogy.internal}.</p>
 */
@Service
@RequiredArgsConstructor
public class LunarCalendarServiceImpl implements LunarCalendarService {

    private final VietnameseLunarCalendarConverter converter;

    @Override
    public LunarDateDto convertSolarToLunar(LocalDate solarDate) {
        VietnameseLunarDate lunar = converter.convertSolarToLunar(solarDate);
        if (lunar == null) {
            return null;
        }
        return LunarDateDto.builder()
                .day(lunar.day())
                .month(lunar.month())
                .year(lunar.year())
                .leap(lunar.leap())
                .canChi(lunar.canChiYear())
                .build();
    }

    @Override
    public String canChiOfYear(int lunarYear) {
        return converter.getCanChiYear(lunarYear);
    }
}
