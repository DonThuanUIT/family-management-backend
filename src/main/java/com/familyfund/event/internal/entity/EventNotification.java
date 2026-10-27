package com.familyfund.event.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity mapping table `event_notifications` (Flyway V1).
 * Tracks announcements and schedule change notifications sent to clan members.
 */
@Entity
@Table(name = "event_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "event")
public class EventNotification {

    @Id
    @Column(name = "notification_id", length = 20, nullable = false, updatable = false)
    private String notificationId;

    @Column(name = "event_id", length = 20, nullable = false)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    private Event event;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content_changes", columnDefinition = "TEXT", nullable = false)
    private String contentChanges;

    @CreationTimestamp
    @Column(name = "sent_time", updatable = false)
    private Instant sentTime;

    @Column(name = "triggered_by", length = 20, nullable = false, updatable = false)
    private String triggeredBy;

    @PrePersist
    public void prePersist() {
        if (this.sentTime == null) {
            this.sentTime = Instant.now();
        }
    }
}
