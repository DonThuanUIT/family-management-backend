package com.familyfund.event.internal.entity;

import com.familyfund.event.api.dto.EventRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite primary key for `event_personnel` (event_id, member_id, event_role).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventPersonnelId implements Serializable {
    private String eventId;
    private String memberId;
    private EventRole eventRole;
}
