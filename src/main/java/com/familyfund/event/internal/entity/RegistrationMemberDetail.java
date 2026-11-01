package com.familyfund.event.internal.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity mapping table `registration_member_details` (Flyway V1).
 * Represents individual family members attending the event and check-in status.
 */
@Entity
@Table(name = "registration_member_details")
@IdClass(RegistrationMemberDetailId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "registration")
public class RegistrationMemberDetail {

    @Id
    @Column(name = "registration_id", length = 20, nullable = false)
    private String registrationId;

    @Id
    @Column(name = "member_id", length = 20, nullable = false)
    private String memberId;

    @Column(name = "is_checked_in")
    @Builder.Default
    private Boolean isCheckedIn = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", insertable = false, updatable = false)
    private FamilyRegistration registration;

    @PrePersist
    public void prePersist() {
        if (this.isCheckedIn == null) {
            this.isCheckedIn = false;
        }
    }
}
