package com.familyfund.event.api;

import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.event.api.dto.RegistrationSummaryDto;

import java.util.Collection;
import java.util.Map;
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

    /**
     * Retrieves aggregated attendee and meal metrics for an event (US-06).
     */
    Optional<RegistrationSummaryDto> getRegistrationSummary(String eventId);

    /**
     * Finds summary of an event task by ID for cross-module advance vouchers (US-46).
     */
    Optional<com.familyfund.event.api.dto.EventTaskSummaryDto> findTaskSummary(String taskId);

    /**
     * Resolves member IDs of all event personnel / organizers (HeadOfOrganizer, OrganizerMember, EventTreasurer)
     * for a given event (US-48).
     */
    Set<String> findOrganizerMemberIds(String eventId);

    /**
     * Checks whether a member currently holds a specific contextual event role (e.g., {@code EventTreasurer})
     * in any event belonging to the specified clan (US-60).
     */
    boolean hasEventRoleInClan(String clanId, String memberId, EventRole eventRole);

    /**
     * Batch-resolves event names for a collection of event IDs (US-62).
     *
     * @param eventIds collection of event IDs
     * @return map of eventId to eventName
     */
    Map<String, String> findEventNamesByIds(Collection<String> eventIds);
}
