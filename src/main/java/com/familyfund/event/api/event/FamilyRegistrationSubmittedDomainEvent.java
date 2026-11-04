package com.familyfund.event.api.event;

import java.time.Instant;

/**
 * Domain event published when a family registration is submitted for an event (US-06).
 */
public record FamilyRegistrationSubmittedDomainEvent(
        String registrationId,
        String eventId,
        String clanId,
        String representativeMemberId,
        int totalAttendees,
        int vegetarianMeals,
        int savoryMeals,
        Instant submittedAt,
        String triggeredBy
) {
}
