package com.familyfund.event.internal.entity;

import com.familyfund.event.api.dto.EventStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity mapping table `events` (Flyway V1, V3, V5).
 * Represents a family event (ancestor memorial, clan gathering, etc.).
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"personnel", "tasks", "registrations"})
public class Event {

    @Id
    @Column(name = "event_id", length = 20, nullable = false, updatable = false)
    private String eventId;

    @Column(name = "clan_id", length = 20, nullable = false)
    private String clanId;

    @Column(name = "event_name", length = 100, nullable = false)
    private String eventName;

    @Column(name = "event_datetime", nullable = false)
    private Instant eventDatetime;

    @Column(name = "event_date_lunar", length = 50)
    private String eventDateLunar;

    @Column(name = "location", length = 255, nullable = false)
    private String location;

    @Column(name = "estimated_budget", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal estimatedBudget = BigDecimal.ZERO;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25, nullable = false)
    @Builder.Default
    private EventStatus status = EventStatus.Draft;

    @Column(name = "created_by", length = 20, nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EventPersonnel> personnel = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EventTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FamilyRegistration> registrations = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = EventStatus.Draft;
        }
        if (this.estimatedBudget == null) {
            this.estimatedBudget = BigDecimal.ZERO;
        }
    }
}
