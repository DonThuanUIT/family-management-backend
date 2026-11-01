package com.familyfund.event.api.dto;

/**
 * Status lifecycle of an event task (US-07).
 * Matches the CHECK constraint in table `event_tasks`.
 */
public enum TaskStatus {
    Unassigned,
    InProgress,
    Completed,
    Overdue;

    /**
     * Checks if the transition from this status to the target status is allowed.
     * Life cycle:
     * - Unassigned -> InProgress
     * - InProgress -> Completed, Overdue, or Unassigned (reverted / re-assigned)
     * - Overdue -> Completed or InProgress (if deadline extended)
     * - Completed -> InProgress (if re-opened for adjustments)
     *
     * @param target the desired next status
     * @return true if the transition is allowed according to business invariants
     */
    public boolean canTransitionTo(TaskStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return switch (this) {
            case Unassigned -> target == InProgress;
            case InProgress -> target == Completed || target == Overdue || target == Unassigned;
            case Overdue -> target == Completed || target == InProgress;
            case Completed -> target == InProgress;
        };
    }
}

