package com.familyfund.event.internal.entity;

import com.familyfund.event.api.dto.EventRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity mapping table `event_personnel`.
 * Stores contextual roles assigned for an event (HeadOfOrganizer, EventTreasurer, OrganizerMember).
 */
@Entity
@Table(name = "event_personnel")
@IdClass(EventPersonnelId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "event")
public class EventPersonnel {

    @Id
    @Column(name = "event_id", length = 20, nullable = false)
    private String eventId;

    @Id
    @Column(name = "member_id", length = 20, nullable = false)
    private String memberId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "event_role", length = 30, nullable = false)
    private EventRole eventRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    private Event event;

    @Column(name = "is_read_only", nullable = false)
    @Builder.Default
    private Boolean isReadOnly = false;

    @CreationTimestamp
    @Column(name = "assigned_date", updatable = false)
    private Instant assignedDate;

    @PrePersist
    public void prePersist() {
        if (isReadOnly == null) {
            isReadOnly = false;
        }
        if (assignedDate == null) {
            assignedDate = Instant.now();
        }
    }
}
