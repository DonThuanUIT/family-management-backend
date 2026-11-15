package com.familyfund.event.internal.anniversary;

import com.familyfund.event.api.dto.DeathAnniversaryDto;
import com.familyfund.event.api.dto.EventSuggestionDto;
import com.familyfund.event.internal.util.AnniversaryKeyGenerator;
import com.familyfund.genealogy.api.dto.DeceasedMemberDto;
import com.familyfund.genealogy.api.dto.LunarDateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Builds the {@link DeathAnniversaryDto} (and its "Đám giỗ" suggestion) for one deceased member on one reference day
 * (US-40).
 *
 * <p>Pure computation on purpose — no repository, no transaction, no clock of its own (the caller passes
 * {@code today}), so the whole anniversary math is unit-testable with fixed dates.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeathAnniversaryCalculator {

    private final LunarAnniversaryResolver resolver;
    private final AnniversaryKeyGenerator keyGenerator;
    private final DeathAnniversaryProperties properties;

    /**
     * Resolves this year's anniversary of one member.
     *
     * @param member  deceased member with a lunar anniversary string
     * @param today   reference day
     * @param zone    clan timezone
     * @param lunar   lunar converter (from {@code genealogy::api})
     * @param covered {@code true} when an event already exists for this anniversary
     * @return the anniversary view, or empty when the stored lunar string cannot be resolved to a solar date
     */
    public Optional<DeathAnniversaryDto> compute(DeceasedMemberDto member,
                                                 LocalDate today,
                                                 ZoneId zone,
                                                 LunarCalendarPort lunar,
                                                 boolean covered) {
        if (member == null || today == null) {
            return Optional.empty();
        }
        Optional<LunarAnniversaryResolver.LunarAnniversary> parsed = resolver.parse(member.deathDateLunar());
        if (parsed.isEmpty()) {
            log.debug("US-40: member [{}] has an unparsable lunar anniversary [{}]; skipped",
                    member.memberId(), member.deathDateLunar());
            return Optional.empty();
        }

        // The anniversary of this solar year, or of the next one when this year's already passed.
        LocalDate anniversaryDate = resolveAnniversaryDate(parsed.get(), today, lunar);
        if (anniversaryDate == null) {
            log.debug("US-40: cannot resolve the lunar anniversary [{}] of member [{}] near {}",
                    member.deathDateLunar(), member.memberId(), today);
            return Optional.empty();
        }

        long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, anniversaryDate);
        int anniversaryYear = anniversaryDate.getYear();
        LunarDateDto anniversaryLunar = lunar.convertSolarToLunar(anniversaryDate);

        Integer matchedMilestone = properties.milestones().stream()
                .filter(milestone -> milestone == daysRemaining)
                .findFirst()
                .orElse(null);

        EventSuggestionDto suggestion = buildSuggestion(member, anniversaryDate, anniversaryLunar, daysRemaining);

        return Optional.of(DeathAnniversaryDto.builder()
                .memberId(member.memberId())
                .memberFullName(member.fullName())
                .generation(member.generation())
                .branchId(member.branchId())
                .branchName(member.branchName())
                .deathDate(member.deathDate())
                .deathDateLunar(member.deathDateLunar())
                .lunarDay(parsed.get().day())
                .lunarMonth(parsed.get().month())
                .leapMonth(parsed.get().leap())
                .anniversaryYear(anniversaryYear)
                .anniversaryDate(anniversaryDate)
                .daysRemaining(daysRemaining)
                .milestoneDays(matchedMilestone)
                .reminderTriggered(false)
                .alreadyReminded(false)
                .suggestion(covered ? null : suggestion)
                .computedAt(Instant.now())
                .build());
    }

    /**
     * Picks the anniversary that is {@code today} or the next occurrence.
     *
     * <p>Only the current and the next solar year are considered: the lunar year and the solar year never drift more
     * than that, and stopping early keeps the daily pass cheap.</p>
     */
    private LocalDate resolveAnniversaryDate(LunarAnniversaryResolver.LunarAnniversary anniversary,
                                             LocalDate today,
                                             LunarCalendarPort lunar) {
        for (int year : new int[]{today.getYear(), today.getYear() + 1}) {
            Optional<LocalDate> resolved = resolver.resolveSolarDate(anniversary, year, lunar);
            if (resolved.isPresent() && !resolved.get().isBefore(today)) {
                return resolved.get();
            }
        }
        return null;
    }

    private EventSuggestionDto buildSuggestion(DeceasedMemberDto member,
                                               LocalDate anniversaryDate,
                                               LunarDateDto anniversaryLunar,
                                               long daysRemaining) {
        String lunarLabel = anniversaryLunar != null
                ? anniversaryLunar.toAnniversaryString()
                : member.deathDateLunar();
        String eventName = properties.eventNameTemplate()
                .replace("{name}", member.fullName() == null ? "" : member.fullName())
                .replace("{lunar}", lunarLabel == null ? "" : lunarLabel)
                .replace("{year}", String.valueOf(anniversaryDate.getYear()));
        String location = (member.branchName() != null && !member.branchName().isBlank())
                ? member.branchName().trim()
                : "Nhà thờ họ";
        Instant start = anniversaryDate.atTime(properties.reminderTime())
                .atZone(properties.zone() == null ? ZoneId.of(DeathAnniversaryProperties.DEFAULT_ZONE)
                        : ZoneId.of(properties.zone()))
                .toInstant();
        String description = "Đám giỗ " + (member.fullName() == null ? "" : member.fullName())
                + (member.generation() != null ? " (đời thứ " + member.generation() + ")" : "")
                + " — giỗ năm " + anniversaryDate.getYear()
                + (lunarLabel != null ? ", " + lunarLabel : "")
                + (member.branchName() != null ? ", chi/nhánh " + member.branchName() : "")
                + ". Gợi ý do hệ thống tạo từ lịch giỗ âm lịch (US-40).";

        EventSuggestionDto.CreateEventSuggestion requestBody = EventSuggestionDto.CreateEventSuggestion.builder()
                .eventName(eventName)
                .eventDatetime(start)
                .eventDateLunar(lunarLabel)
                .location(location)
                .estimatedBudget(properties.defaultBudget())
                .description(description)
                .build();

        return EventSuggestionDto.builder()
                .suggestionId(keyGenerator.generateSuggestionId())
                .memberId(member.memberId())
                .eventName(eventName)
                .eventDatetime(start)
                .eventDateLunar(lunarLabel)
                .location(location)
                .estimatedBudget(properties.defaultBudget())
                .description(description)
                .daysRemaining(daysRemaining)
                .requestBody(requestBody)
                .build();
    }
}
