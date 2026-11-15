package com.familyfund.event.internal.scheduler;

import com.familyfund.event.internal.service.TaskProgressAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background deadline worker shared by two user stories. It hosts two independent scheduled passes:
 *
 * <ol>
 *   <li>{@code scanUpcomingTaskDeadlines()} (US-08): warns the assigned member when the deadline is
 *       24 hours away or less ({@code Reminder24H}).</li>
 *   <li>{@code scanOverdueTaskDeadlines()} (US-47): escalates to the branch representative when the
 *       deadline went past at least 12 hours ago ({@code OverdueAlert12H}).</li>
 * </ol>
 *
 * <p>Intervals are configurable through {@code app.task.deadline-warning.*} and
 * {@code app.task.deadline-overdue.*}; the first pass of each is delayed so that application startup is
 * never blocked by the scan. Any unexpected failure is logged and swallowed: a broken pass must not kill
 * the scheduled task for the rest of the application lifetime.</p>
 *
 * <p>The whole bean can be switched off with {@code app.task.deadline-warning.enabled=false}
 * (test suites do this so a scan never mutates a shared database); the switch covers both passes.</p>
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

    /**
     * US-47 escalation pass: tasks that are at least 12 hours late are reported to the representative of
     * the branch responsible for them, once per task.
     */
    @Scheduled(
            fixedDelayString = "${app.task.deadline-overdue.interval-ms:900000}",
            initialDelayString = "${app.task.deadline-overdue.initial-delay-ms:120000}"
    )
    public void scanOverdueTaskDeadlines() {
        try {
            int issued = taskProgressAlertService.scanAndEscalateOverdueTasks();
            log.debug("US-47 escalation scan finished: {} escalation(s) issued", issued);
        } catch (RuntimeException ex) {
            log.error("US-47 escalation scan failed; the next pass will retry", ex);
        }
    }
}