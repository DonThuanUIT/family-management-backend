package com.familyfund.event.api.dto;

/**
 * Status lifecycle of a family event registration (US-06).
 *
 * <p>Allowed states:
 * <ul>
 *   <li>{@link #Confirmed}: The registration is officially confirmed and counted in event attendees.</li>
 *   <li>{@link #PendingModification}: The representative requested modifying attendee/meal counts, awaiting organizer approval.</li>
 *   <li>{@link #PendingCancellation}: The representative requested cancelling the registration, awaiting organizer approval.</li>
 *   <li>{@link #Cancelled}: The registration was approved for cancellation and is excluded from headcount/meals.</li>
 * </ul>
 * </p>
 */
public enum RegistrationStatus {
    Confirmed,
    PendingModification,
    PendingCancellation,
    Cancelled;

    /**
     * @return true if this registration is cancelled and should not be counted in attendees/meals
     */
    public boolean isCancelled() {
        return this == Cancelled;
    }

    /**
     * @return true if there is an outstanding modification or cancellation request requiring review
     */
    public boolean isPendingReview() {
        return this == PendingModification || this == PendingCancellation;
    }
}
