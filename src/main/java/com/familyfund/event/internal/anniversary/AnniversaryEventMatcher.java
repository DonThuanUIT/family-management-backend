package com.familyfund.event.internal.anniversary;

import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Answers "is this anniversary already covered by an event?" for US-40.
 *
 * <p>A reminder that keeps suggesting the same "Đám giỗ" after the user already created that event is noise, so the
 * anniversary is marked as covered when an event of the clan matches either:</p>
 * <ul>
 *   <li>the same solar day ({@code event_datetime} inside {@code [anniversaryDate 00:00, +1 day)} in the clan
 *       timezone), or</li>
 *   <li>an identical lunar label ({@code event_date_lunar} trimmed, case-insensitive) inside the same solar year.</li>
 * </ul>
 *
 * <p>Deliberately a heuristic and not a foreign key: an event has no column pointing at a member, and adding one is a
 * schema decision outside US-40.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnniversaryEventMatcher {

    private final EventRepository eventRepository;

    /**
     * @param clanId          tenant
     * @param anniversaryDate solar date of the anniversary
     * @param lunarLabel      stored lunar label, e.g. {@code "15/02 Giáp Thìn"} (may be {@code null})
     * @param zone            timezone the solar day is evaluated in
     * @return {@code true} when an event of the clan already covers this anniversary
     */
    public boolean isCoveredByExistingEvent(String clanId, LocalDate anniversaryDate, String lunarLabel, ZoneId zone) {
        if (clanId == null || clanId.isBlank() || anniversaryDate == null) {
            return false;
        }
        Instant from = anniversaryDate.atStartOfDay(zone).toInstant();
        Instant to = anniversaryDate.plusDays(1).atStartOfDay(zone).toInstant();

        List<Event> eventsOnAnniversaryDay = eventRepository.findEventsInWindow(clanId, from, to);
        if (!eventsOnAnniversaryDay.isEmpty()) {
            return true;
        }
        if (lunarLabel == null || lunarLabel.isBlank()) {
            return false;
        }
        // Same lunar label anywhere in the anniversary year also counts (the event may start on another day).
        Instant yearStart = LocalDate.of(anniversaryDate.getYear(), 1, 1).atStartOfDay(zone).toInstant();
        Instant yearEnd = LocalDate.of(anniversaryDate.getYear() + 1, 1, 1).atStartOfDay(zone).toInstant();
        return eventRepository.findEventsInWindow(clanId, yearStart, yearEnd).stream()
                .anyMatch(event -> event.getEventDateLunar() != null
                        && event.getEventDateLunar().trim().equalsIgnoreCase(lunarLabel.trim()));
    }
}
