package com.familyfund.event.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Entity mapping table `death_anniversary_reminders` (Flyway V24): the send-ledger of US-40.
 *
 * <p>One row = "this death anniversary of this member was reminded at this milestone". The unique key
 * {@code (member_id, lunar_year, milestone_days)} is the idempotency guard: a cron pass that runs twice on the same
 * day, or a server restart mid-pass, can never send the same reminder twice.</p>
 */
@Entity
@Table(
        name = "death_anniversary_reminders",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_death_anniversary_reminder",
                columnNames = {"member_id", "lunar_year", "milestone_days"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class DeathAnniversaryReminder {

    /** reminders.reminder_id is VARCHAR(20). */
    public static final int REMINDER_ID_MAX_LENGTH = 20;

    // reminder_id VARCHAR(20) PRIMARY KEY
    @Id
    @Column(name = "reminder_id", length = REMINDER_ID_MAX_LENGTH, nullable = false, updatable = false)
    private String reminderId;

    // clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE
    @Column(name = "clan_id", length = 20, nullable = false, updatable = false)
    private String clanId;

    // member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE CASCADE
    @Column(name = "member_id", length = 20, nullable = false, updatable = false)
    private String memberId;

    // anniversary_year INT NOT NULL
    @Column(name = "anniversary_year", nullable = false, updatable = false)
    private Integer anniversaryYear;

    // lunar_year INT NOT NULL
    @Column(name = "lunar_year", nullable = false, updatable = false)
    private Integer lunarYear;

    // milestone_days INT NOT NULL CHECK (milestone_days >= 0)
    @Column(name = "milestone_days", nullable = false, updatable = false)
    private Integer milestoneDays;

    // anniversary_date DATE NOT NULL
    @Column(name = "anniversary_date", nullable = false, updatable = false)
    private LocalDate anniversaryDate;

    // trigger_date DATE NOT NULL
    @Column(name = "trigger_date", nullable = false, updatable = false)
    private LocalDate triggerDate;

    // recipient_count INT NOT NULL DEFAULT 0
    @Column(name = "recipient_count", nullable = false)
    @Builder.Default
    private Integer recipientCount = 0;

    // created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.recipientCount == null) {
            this.recipientCount = 0;
        }
    }
}
