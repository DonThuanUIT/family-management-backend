package com.familyfund.genealogy.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Entity mapping table `member_privacy_settings` (US-25).
 * Stores member personal data privacy consent settings.
 */
@Entity
@Table(name = "member_privacy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberPrivacySetting {

    @Id
    @Column(name = "member_id", length = 20, nullable = false, updatable = false)
    private String memberId;

    @Column(name = "share_date_of_birth", nullable = false)
    @Builder.Default
    private boolean shareDateOfBirth = false;

    @Column(name = "share_phone_number", nullable = false)
    @Builder.Default
    private boolean sharePhoneNumber = false;

    @Column(name = "share_email", nullable = false)
    @Builder.Default
    private boolean shareEmail = false;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }
}
