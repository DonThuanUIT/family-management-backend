package com.familyfund.finance.internal.entity;

import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity mapping table {@code ledger_adjustment_requests} (US-15).
 */
@Entity
@Table(name = "ledger_adjustment_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerAdjustmentRequest {

    @Id
    @Column(name = "request_id", length = 20, nullable = false, updatable = false)
    private String requestId;

    @Column(name = "original_transaction_id", length = 20, nullable = false, updatable = false)
    private String originalTransactionId;

    @Column(name = "proposed_adjustment_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal proposedAdjustmentAmount;

    @Column(name = "adjustment_reason", columnDefinition = "TEXT", nullable = false)
    private String adjustmentReason;

    @Column(name = "evidence_attachment_url", length = 500)
    private String evidenceAttachmentUrl;

    @Column(name = "proposed_by_account_id", length = 20, nullable = false, updatable = false)
    private String proposedByAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20, nullable = false)
    @Builder.Default
    private LedgerAdjustmentStatus approvalStatus = LedgerAdjustmentStatus.Pending;

    @Column(name = "reviewed_by_account_id", length = 20)
    private String reviewedByAccountId;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (approvalStatus == null) {
            approvalStatus = LedgerAdjustmentStatus.Pending;
        }
    }
}
