package com.familyfund.event.internal.entity;

import com.familyfund.event.api.dto.RegistrationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity mapping table `family_registrations` (Flyway V1, V10).
 * Represents a household registration for a family event (US-06).
 */
@Entity
@Table(name = "family_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"event", "memberDetails"})
public class FamilyRegistration {

    @Id
    @Column(name = "registration_id", length = 20, nullable = false, updatable = false)
    private String registrationId;

    @Column(name = "event_id", length = 20, nullable = false)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    private Event event;

    @Column(name = "representative_member_id", length = 20, nullable = false)
    private String representativeMemberId;

    @Column(name = "total_attendees", nullable = false)
    private Integer totalAttendees;

    @Column(name = "vegetarian_meals", nullable = false)
    @Builder.Default
    private Integer vegetarianMeals = 0;

    @Column(name = "savory_meals", nullable = false)
    @Builder.Default
    private Integer savoryMeals = 0;

    @Column(name = "dietary_requirements", length = 255)
    private String dietaryRequirements;

    @Column(name = "transportation_notes", length = 255)
    private String transportationNotes;

    @Column(name = "registration_date")
    @Builder.Default
    private Instant registrationDate = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25, nullable = false)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.Confirmed;

    // Approval workflow fields for modifications / cancellations (US-06)
    @Column(name = "proposed_total_attendees")
    private Integer proposedTotalAttendees;

    @Column(name = "proposed_vegetarian_meals")
    private Integer proposedVegetarianMeals;

    @Column(name = "proposed_savory_meals")
    private Integer proposedSavoryMeals;

    @Column(name = "proposed_dietary_requirements", length = 255)
    private String proposedDietaryRequirements;

    @Column(name = "proposed_transportation_notes", length = 255)
    private String proposedTransportationNotes;

    @Column(name = "modification_reason", length = 255)
    private String modificationReason;

    @Column(name = "reviewed_by_account_id", length = 20)
    private String reviewedByAccountId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RegistrationMemberDetail> memberDetails = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.vegetarianMeals == null) {
            this.vegetarianMeals = 0;
        }
        if (this.savoryMeals == null) {
            this.savoryMeals = 0;
        }
        if (this.registrationDate == null) {
            this.registrationDate = Instant.now();
        }
        if (this.status == null) {
            this.status = RegistrationStatus.Confirmed;
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
