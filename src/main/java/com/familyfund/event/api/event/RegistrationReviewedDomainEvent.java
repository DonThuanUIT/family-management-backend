package com.familyfund.event.api.event;

import java.time.Instant;

/**
 * Domain event published when an event organizer reviews (approves/rejects) a registration
 * modification or cancellation request (US-06).
 */
public record RegistrationReviewedDomainEvent(
        String registrationId,
        String eventId,
        String clanId,
        String representativeMemberId,
        boolean approved,
        String reviewerAccountId,
        String rejectionReason,
        Instant reviewedAt
) {
}
