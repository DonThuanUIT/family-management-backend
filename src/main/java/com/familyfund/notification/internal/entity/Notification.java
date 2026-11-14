package com.familyfund.notification.internal.entity;

import com.familyfund.notification.api.NotificationCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
 * Entity mapping table `notifications` (Flyway V23), column-for-column.
 *
 * <p>One row = one notification in one account's personal inbox. `recipient_account_id` is the owner, so every
 * query of the inbox filters on it. `category` is the three-way split of US-39 (Event / Task / Finance) and
 * matches the `CHECK` constraint of the table; `is_read` / `read_at` implement the mark-as-read API.</p>
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Notification {

    // notification_id VARCHAR(20) PRIMARY KEY
    @Id
    @Column(name = "notification_id", length = 20, nullable = false, updatable = false)
    private String notificationId;

    // clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE
    @Column(name = "clan_id", length = 20, nullable = false, updatable = false)
    private String clanId;

    // recipient_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE CASCADE
    @Column(name = "recipient_account_id", length = 20, nullable = false, updatable = false)
    private String recipientAccountId;

    // category VARCHAR(20) NOT NULL CHECK (category IN ('Event', 'Task', 'Finance'))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false, updatable = false)
    private NotificationCategory category;

    // title VARCHAR(200) NOT NULL
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    // content TEXT NOT NULL
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    // reference_type VARCHAR(30)
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    // reference_id VARCHAR(20)
    @Column(name = "reference_id", length = 20)
    private String referenceId;

    // is_read BOOLEAN NOT NULL DEFAULT FALSE
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = Boolean.FALSE;

    // read_at TIMESTAMPTZ
    @Column(name = "read_at")
    private Instant readAt;

    // created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        // Mirror the DB defaults so explicit nulls from the builder never override them
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.isRead == null) {
            this.isRead = Boolean.FALSE;
        }
    }
}
