package com.familyfund.event.api.dto;

/**
 * Lifecycle statuses of an event.
 * Matches the CHECK constraint in table `events`.
 */
public enum EventStatus {
    Draft,
    RegistrationOpen,
    RegistrationClosed,
    Ongoing,
    Ended,
    Closed;

    /**
     * Checks if the transition from this status to the target status is allowed.
     * Life cycle: Draft -> RegistrationOpen -> RegistrationClosed -> Ongoing -> Ended -> Closed.
     *
     * @param target the desired next status
     * @return true if the transition is allowed according to business invariants
     */
    public boolean canTransitionTo(EventStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case Draft -> target == RegistrationOpen;
            case RegistrationOpen -> target == RegistrationClosed;
            case RegistrationClosed -> target == Ongoing;
            case Ongoing -> target == Ended;
            case Ended -> target == Closed;
            case Closed -> false;
        };
    }
}
