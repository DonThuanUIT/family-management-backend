package com.familyfund.event.api;

import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventSummaryDto;

import java.util.Optional;
import java.util.Set;

/**
 * Public query service provided by the Event module for cross-module consumption.
 */
public interface EventQueryService {

    /**
     * Finds summary of an event by ID.
     */
    Optional<EventSummaryDto> findEventSummary(String eventId);

    /**
     * Checks if the event is closed.
     */
    boolean isEventClosed(String eventId);

    /**
     * Gets all contextual roles held by a member for a given event.
     */
    Set<EventRole> getMemberRolesInEvent(String eventId, String memberId);
}
