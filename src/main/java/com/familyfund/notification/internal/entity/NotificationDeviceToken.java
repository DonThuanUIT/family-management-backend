package com.familyfund.notification.internal.entity;

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
import lombok.ToString;

import java.time.Instant;

/**
 * Entity mapping table `notification_device_tokens` (Flyway V23): the FCM registration tokens that push
 * notifications are delivered to (US-39).
 *
 * <p>`fcm_token` is UNIQUE, so a device keeps exactly one row no matter how many accounts log in on it; the row is
 * moved to the account that registered it last. `is_active = false` marks a token that was revoked on logout or
 * rejected by Firebase, and such tokens are skipped when pushing.</p>
 */
@Entity
@Table(name = "notification_device_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class NotificationDeviceToken {

    public static final String PLATFORM_ANDROID = "Android";

    // token_id VARCHAR(20) PRIMARY KEY
    @Id
    @Column(name = "token_id", length = 20, nullable = false, updatable = false)
    private String tokenId;

    // account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE CASCADE
    @Column(name = "account_id", length = 20, nullable = false)
    private String accountId;

    // fcm_token VARCHAR(255) NOT NULL UNIQUE
    @Column(name = "fcm_token", length = 255, nullable = false, unique = true)
    private String fcmToken;

    // platform VARCHAR(20) NOT NULL DEFAULT 'Android' CHECK (platform IN ('Android', 'IOS', 'Web'))
    @Column(name = "platform", length = 20, nullable = false)
    @Builder.Default
    private String platform = PLATFORM_ANDROID;

    // is_active BOOLEAN NOT NULL DEFAULT TRUE
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = Boolean.TRUE;

    // created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    // last_seen_at TIMESTAMPTZ
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.isActive == null) {
            this.isActive = Boolean.TRUE;
        }
        if (this.platform == null || this.platform.isBlank()) {
            this.platform = PLATFORM_ANDROID;
        }
    }
}
