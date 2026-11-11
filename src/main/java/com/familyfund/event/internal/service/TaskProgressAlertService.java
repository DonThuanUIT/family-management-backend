package com.familyfund.event.internal.service;

/**
 * Application service backing the US-08 background worker.
 * Scans every clan for unfinished event tasks whose deadline is at most 24 hours away,
 * records a {@code ProgressWarning} log (`task_progress_alerts`, level {@code Reminder24H})
 * and notifies the assigned member through {@link com.familyfund.event.internal.notification.NotificationSender}.
 */
public interface TaskProgressAlertService {

    /**
     * Runs one deadline scan pass.
     *
     * <p>Idempotent per pass: a task that already produced a {@code Reminder24H} alert is skipped,
     * completed tasks are ignored, and a failure on a single task never aborts the whole pass.</p>
     *
     * @return number of progress warnings newly issued during this pass
     */
    int scanAndWarnUpcomingDeadlines();
}