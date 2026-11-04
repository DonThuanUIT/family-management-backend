package com.familyfund.event.api.event;

import java.time.Instant;

/**
 * Domain event published when a family representative requests modifying or cancelling
 * an existing registration, awaiting organizer review (US-06).
 */
public record RegistrationModificationRequestedDomainEvent(
        String registrationId,
        String eventId,
        String clanId,
        String representativeMemberId,
        String modificationType, // "MODIFICATION" or "CANCELLATION"
        String reason,
        Instant requestedAt,
        String triggeredBy
) {
}
