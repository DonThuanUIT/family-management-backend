package com.familyfund.event.api.event;

import java.time.Instant;

/**
 * Domain event published when an event lifecycle transitions to RegistrationOpen.
 */
public record EventOpenedDomainEvent(
        String eventId,
        String clanId,
        String eventName,
        Instant eventDatetime,
        String eventDateLunar,
        String location,
        String triggeredBy
) {
}
