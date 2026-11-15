package com.familyfund.event.api.event;

import java.time.LocalDate;
import java.util.List;

/**
 * Domain event published when the daily job has recorded a death-anniversary reminder (US-40).
 *
 * <p>The {@code event} module resolves WHO should be reminded (branch representative + branch members) and records
 * the send-ledger row; the {@code notification} module turns this event into inbox rows. Publishing an event keeps
 * the dependency one-way: {@code notification} already depends on {@code event::api}, so {@code event} must not call
 * {@code NotificationService} directly.</p>
 *
 * @param clanId               tenant of the deceased member
 * @param memberId             deceased member whose anniversary is coming
 * @param memberFullName       display name used in the reminder
 * @param branchId             branch of the deceased
 * @param branchName           branch display name (may be {@code null})
 * @param anniversaryDate      solar date of this year's anniversary
 * @param anniversaryYear      solar year of the anniversary
 * @param lunarLabel           lunar label of the anniversary, e.g. {@code "15/02 Bính Ngọ"}
 * @param milestoneDays        configured milestone that triggered the reminder (14, 3, ...)
 * @param daysRemaining       days from the trigger day to the anniversary
 * @param title                notification title, already rendered
 * @param content              notification body, already rendered
 * @param recipientAccountIds  accounts that must receive the inbox notification (may be empty)
 */
public record DeathAnniversaryReminderIssuedDomainEvent(
        String clanId,
        String memberId,
        String memberFullName,
        String branchId,
        String branchName,
        LocalDate anniversaryDate,
        Integer anniversaryYear,
        String lunarLabel,
        Integer milestoneDays,
        long daysRemaining,
        String title,
        String content,
        List<String> recipientAccountIds
) {}
