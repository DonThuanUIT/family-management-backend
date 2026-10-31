package com.familyfund.contribution.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity mapping table `event_item_benchmarks`.
 * Predefined value benchmarks set by the event organizing committee (BTC) for standard in-kind offerings.
 */
@Entity
@Table(name = "event_item_benchmarks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventItemBenchmark {

    @Id
    @Column(name = "benchmark_id", length = 20, nullable = false, updatable = false)
    private String benchmarkId;

    @Column(name = "event_id", length = 20, nullable = false)
    private String eventId;

    @Column(name = "clan_id", length = 20, nullable = false)
    private String clanId;

    @Column(name = "item_name", length = 100, nullable = false)
    private String itemName;

    @Column(name = "unit", length = 20, nullable = false)
    private String unit;

    @Column(name = "unit_price", precision = 18, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "note", length = 255)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
