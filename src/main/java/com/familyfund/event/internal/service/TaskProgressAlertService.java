package com.familyfund.event.internal.service;

/**
 * Application service backing the background deadline worker shared by two user stories.
 * Scans every clan for unfinished event tasks whose deadline is at most 24 hours away,
 * records a {@code ProgressWarning} log (`task_progress_alerts`, level {@code Reminder24H})
 * and notifies the assigned member through {@link com.familyfund.event.internal.notification.NotificationSender}.
 *
 * <p>The escalation pass of US-47 ({@link #scanAndEscalateOverdueTasks()}) works the other way around: for
 * tasks that are already 12 hours late it records an {@code OverdueAlert12H} log and notifies the branch
 * representative instead of the assignee.</p>
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

    /**
     * Runs one overdue escalation pass (US-47).
     *
     * <p>Finds every unfinished task whose deadline went past at least 12 hours ago, records one
     * {@code OverdueAlert12H} log (`task_progress_alerts`) addressed to the representative of the
     * responsible branch, and sends that manager a notification naming the task, the person in charge
     * and how many hours it is late.</p>
     *
     * <p>Idempotent per task: a task that already produced an {@code OverdueAlert12H} alert is skipped,
     * so a task that stays overdue for days is escalated exactly once. A failure on a single task never
     * aborts the whole pass.</p>
     *
     * @return number of escalations newly issued during this pass
     */
    int scanAndEscalateOverdueTasks();
}