package com.familyfund.event.api.dto;

import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One death anniversary of one deceased member, as seen from "today" (US-40).
 *
 * <p>Produced by the daily cron job and by the on-demand endpoint. It carries everything the UI needs to show the
 * reminder and to pre-fill the "create Đám giỗ event" form, so the client never has to re-derive lunar dates.</p>
 *
 * @param memberId           {@code members.member_id} of the deceased
 * @param memberFullName     display name used in the reminder text
 * @param generation         generation of the deceased inside the clan (for the ceremonial wording)
 * @param branchId           branch the deceased belongs to
 * @param branchName         branch display name, or {@code null} when the branch row is missing
 * @param deathDate          solar death date
 * @param deathDateLunar     the stored lunar anniversary string, e.g. {@code "15/02 Giáp Thìn"}
 * @param lunarDay           lunar day of the anniversary (1..30); {@code null} when the stored string is unparsable
 * @param lunarMonth         lunar month of the anniversary (1..12); {@code null} when unparsable
 * @param leapMonth          {@code true} when the anniversary falls in a leap month
 * @param anniversaryYear    solar year in which this year's anniversary happens
 * @param anniversaryDate    solar date of this year's anniversary
 * @param daysRemaining      days from "today" to {@code anniversaryDate} (0 = today is the anniversary day)
 * @param milestoneDays      configured milestone that matched, or {@code null} when no milestone matched
 * @param reminderTriggered  {@code true} when this call/pass has (or would have) sent a reminder
 * @param alreadyReminded    {@code true} when this milestone was already sent for this member earlier
 * @param suggestion         the "Đám giỗ" event payload suggested for this anniversary
 */
@Builder
public record DeathAnniversaryDto(
        String memberId,
        String memberFullName,
        Integer generation,
        String branchId,
        String branchName,
        LocalDate deathDate,
        String deathDateLunar,
        Integer lunarDay,
        Integer lunarMonth,
        Boolean leapMonth,
        Integer anniversaryYear,
        LocalDate anniversaryDate,
        long daysRemaining,
        Integer milestoneDays,
        boolean reminderTriggered,
        boolean alreadyReminded,
        EventSuggestionDto suggestion,
        Instant computedAt
) {}
