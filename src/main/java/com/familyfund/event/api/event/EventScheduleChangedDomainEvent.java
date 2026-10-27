package com.familyfund.event.api.event;

/**
 * Domain event published when an event's schedule or location is updated.
 */
public record EventScheduleChangedDomainEvent(
        String eventId,
        String clanId,
        String eventName,
        String title,
        String contentChanges,
        String triggeredBy
) {
}
