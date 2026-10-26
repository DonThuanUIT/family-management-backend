package com.familyfund.event.api.dto;

/**
 * Contextual roles for personnel within an event.
 * Matches the CHECK constraint in table `event_personnel`.
 */
public enum EventRole {
    HeadOfOrganizer,
    OrganizerMember,
    EventTreasurer
}
