package com.familyfund.contribution.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity mapping table `payment_webhooks`.
 * Logs inbound VietQR webhook payment events for idempotency and audit (US-53).
 */
@Entity
@Table(name = "payment_webhooks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentWebhook {

    @Id
    @Column(name = "webhook_id", length = 50, nullable = false, updatable = false)
    private String webhookId;

    @Column(name = "reference_code", length = 100, nullable = false, unique = true)
    private String referenceCode;

    @Column(name = "gateway", length = 50)
    private String gateway;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "transfer_content", length = 500)
    private String transferContent;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "contribution_id", length = 20)
    private String contributionId;

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
