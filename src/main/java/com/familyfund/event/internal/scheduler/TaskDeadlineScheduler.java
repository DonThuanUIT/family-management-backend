package com.familyfund.event.internal.scheduler;

import com.familyfund.event.internal.service.TaskProgressAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background worker of US-08: periodically scans unfinished event tasks and warns the assigned
 * member when the deadline is 24 hours away or less.
 *
 * <p>The interval is configurable through {@code app.task.deadline-warning.interval-ms} and the
 * first pass is delayed by {@code app.task.deadline-warning.initial-delay-ms} so that application
 * startup is never blocked by the scan. Any unexpected failure is logged and swallowed: a broken
 * pass must not kill the scheduled task for the rest of the application lifetime.</p>
 *
 * <p>The bean can be switched off with {@code app.task.deadline-warning.enabled=false}
 * (test suites do this so a scan never mutates a shared database).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.task.deadline-warning.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TaskDeadlineScheduler {

    private final TaskProgressAlertService taskProgressAlertService;

    @Scheduled(
            fixedDelayString = "${app.task.deadline-warning.interval-ms:900000}",
            initialDelayString = "${app.task.deadline-warning.initial-delay-ms:60000}"
    )
    public void scanUpcomingTaskDeadlines() {
        try {
            int issued = taskProgressAlertService.scanAndWarnUpcomingDeadlines();
            log.debug("US-08 deadline scan finished: {} warning(s) issued", issued);
        } catch (RuntimeException ex) {
            log.error("US-08 deadline scan failed; the next pass will retry", ex);
        }
    }
}