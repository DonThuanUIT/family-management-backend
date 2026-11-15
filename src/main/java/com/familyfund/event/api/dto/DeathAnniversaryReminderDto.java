package com.familyfund.event.api.dto;

/**
 * Row of `death_anniversary_reminders` exposed to the client (US-40).
 *
 * @param reminderId      `reminder_id` of the send-ledger row
 * @param memberId        deceased member
 * @param anniversaryYear solar year of the anniversary
 * @param lunarYear       lunar year used for the lookup
 * @param milestoneDays   14 or 3 (configurable)
 * @param anniversaryDate solar date of the anniversary
 * @param triggerDate     day the reminder was sent
 * @param recipientCount  number of accounts that received the inbox notification
 */
public record DeathAnniversaryReminderDto(
        String reminderId,
        String memberId,
        Integer anniversaryYear,
        Integer lunarYear,
        Integer milestoneDays,
        java.time.LocalDate anniversaryDate,
        java.time.LocalDate triggerDate,
        Integer recipientCount
) {}
