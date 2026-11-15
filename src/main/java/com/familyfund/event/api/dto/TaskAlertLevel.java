package com.familyfund.event.api.dto;

/**
 * Alert level of an automatically generated task progress alert (US-08).
 * Matches the CHECK constraint of `task_progress_alerts.alert_level`.
 *
 * <ul>
 *   <li>{@link #Reminder24H}: the {@code ProgressWarning} emitted when an unfinished task
 *       is 24 hours or less away from its deadline.</li>
 *   <li>{@link #OverdueAlert12H}: escalation reserved for tasks overdue by more than 12 hours
 *       (already part of the V1 schema, not produced by the 24h scan).</li>
 * </ul>
 */
public enum TaskAlertLevel {
    Reminder24H,
    OverdueAlert12H
}