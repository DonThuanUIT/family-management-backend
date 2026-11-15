package com.familyfund.event.internal.scheduler;

import com.familyfund.event.api.DeathAnniversaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily cron pass of US-40: compares every deceased member's lunar death anniversary with today's lunar calendar and
 * sends the reminder when a configured milestone (default 14 and 3 days before) is hit.
 *
 * <p>Deliberately a thin wrapper: the work lives in {@link DeathAnniversaryService#scanAndSendReminders} so the tests
 * call it with a fixed date instead of waiting for the trigger. Any failure is logged and swallowed — a broken pass
 * must not kill the scheduled task for the rest of the application lifetime.</p>
 *
 * <p>Switched off with {@code app.death-anniversary.enabled=false} (the test profile does this so a pass never
 * writes into the shared database).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.death-anniversary.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DeathAnniversaryScheduler {

    private final DeathAnniversaryService deathAnniversaryService;

    @Scheduled(
            cron = "${app.death-anniversary.cron:0 0 7 * * *}",
            zone = "${app.death-anniversary.zone:Asia/Ho_Chi_Minh}"
    )
    public void sendDailyDeathAnniversaryReminders() {
        try {
            int sent = deathAnniversaryService.scanAndSendReminders(null);
            log.info("US-40 daily anniversary pass finished: {} reminder(s) issued", sent);
        } catch (RuntimeException ex) {
            log.error("US-40 daily anniversary pass failed; the next run will retry", ex);
        }
    }
}
