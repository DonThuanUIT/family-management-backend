package com.familyfund.identity.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Entity mapping table `account_roles`.
 * Associates user accounts with granted system roles.
 */
@Entity
@Table(name = "account_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AccountRole {

    @EmbeddedId
    private AccountRoleId id;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @PrePersist
    public void prePersist() {
        if (this.assignedAt == null) {
            this.assignedAt = Instant.now();
        }
    }
}
