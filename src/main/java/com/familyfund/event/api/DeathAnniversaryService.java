package com.familyfund.event.api;

import com.familyfund.event.api.dto.DeathAnniversaryDto;
import com.familyfund.event.api.dto.DeathAnniversaryReminderDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Public contract for the daily death-anniversary reminder job (US-40).
 *
 * <p>Two entry points:</p>
 * <ul>
 *   <li>{@link #scanAndSendReminders(LocalDate)} — the cron pass. Converts every deceased member's lunar
 *       anniversary into this year's solar date, and when the distance to that date hits a configured milestone
 *       (default 14 and 3 days) writes the notification into the inbox of the branch representative and of every
 *       member of the branch, then records the send in `death_anniversary_reminders` so it never repeats.</li>
 *   <li>{@link #upcomingAnniversaries(LocalDate, int)} — the read-side used by the API: the anniversaries of the
 *       next N days, each with an {@code EventSuggestionDto} payload the client can send to
 *       {@code POST /api/v1/events} to create the "Đám giỗ" event.</li>
 * </ul>
 *
 * <p>The job never creates events on its own: it only suggests the payload, which keeps a background pass from
 * polluting the event list with drafts nobody asked for.</p>
 */
public interface DeathAnniversaryService {

    /**
     * Runs one reminder pass for the given reference day.
     *
     * @param today reference day in the clan timezone; {@code null} means "today"
     * @return number of reminders actually sent (members × milestones)
     */
    int scanAndSendReminders(LocalDate today);

    /**
     * Upcoming death anniversaries inside the current tenant, ordered by anniversary date.
     *
     * @param from        first day of the window (inclusive)
     * @param windowDays  length of the window in days, e.g. 14
     * @return one entry per deceased member whose anniversary falls in the window
     */
    List<DeathAnniversaryDto> upcomingAnniversaries(LocalDate from, int windowDays);

    /**
     * Reminders already sent for one member (any year), newest first — used by the UI to show "đã nhắc".
     *
     * @param memberId member to inspect
     * @return recorded reminders of that member
     */
    List<DeathAnniversaryReminderDto> remindersOfMember(String memberId);
}
