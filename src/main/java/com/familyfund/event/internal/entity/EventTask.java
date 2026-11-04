package com.familyfund.event.internal.entity;

import com.familyfund.event.api.dto.TaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity mapping table `event_tasks` (Flyway V1).
 * Represents a preparatory task assigned for a family event (US-07).
 */
@Entity
@Table(name = "event_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "event")
public class EventTask {

    @Id
    @Column(name = "task_id", length = 20, nullable = false, updatable = false)
    private String taskId;

    @Column(name = "event_id", length = 20, nullable = false)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    private Event event;

    @Column(name = "task_name", length = 100, nullable = false)
    private String taskName;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "advance_budget", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal advanceBudget = BigDecimal.ZERO;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.Unassigned;

    @Column(name = "assigned_branch_id", length = 20)
    private String assignedBranchId;

    @Column(name = "assigned_member_id", length = 20)
    private String assignedMemberId;

    @Column(name = "created_by", length = 20, nullable = false, updatable = false)
    private String createdBy;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = TaskStatus.Unassigned;
        }
        if (this.advanceBudget == null) {
            this.advanceBudget = BigDecimal.ZERO;
        }
    }
}

