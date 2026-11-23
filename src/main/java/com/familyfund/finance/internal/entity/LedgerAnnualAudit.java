package com.familyfund.finance.internal.entity;

import com.familyfund.finance.api.dto.LedgerAnnualAuditStatus;
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
import java.time.temporal.ChronoUnit;

/**
 * Persistent audit record tracking annual physical cash reconciliation against {@code immutable_ledgers}.
 */
@Entity
@Table(name = "ledger_annual_audits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerAnnualAudit {

    @Id
    @Column(name = "audit_id", length = 20, nullable = false, updatable = false)
    private String auditId;

    @Column(name = "clan_id", length = 20, nullable = false, updatable = false)
    private String clanId;

    @Column(name = "audit_year", nullable = false, updatable = false)
    private Integer auditYear;

    @Column(name = "next_period_year", nullable = false, updatable = false)
    private Integer nextPeriodYear;

    @Column(name = "audit_date", nullable = false, updatable = false)
    private Instant auditDate;

    @Column(name = "system_balance", precision = 18, scale = 2, nullable = false, updatable = false)
    private BigDecimal systemBalance;

    @Column(name = "actual_balance", precision = 18, scale = 2, nullable = false, updatable = false)
    private BigDecimal actualBalance;

    @Column(name = "discrepancy_amount", precision = 18, scale = 2, nullable = false, updatable = false)
    private BigDecimal discrepancyAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_status", length = 25, nullable = false, updatable = false)
    private LedgerAnnualAuditStatus auditStatus;

    @Column(name = "reference_transaction_id", length = 20, updatable = false)
    private String referenceTransactionId;

    @Column(name = "adjustment_request_id", length = 20, unique = true, updatable = false)
    private String adjustmentRequestId;

    @Column(name = "closing_transaction_id", length = 20, unique = true, updatable = false)
    private String closingTransactionId;

    @Column(name = "opening_balance_next_period", precision = 18, scale = 2, updatable = false)
    private BigDecimal openingBalanceNextPeriod;

    @Column(name = "auditor_account_id", length = 20, nullable = false, updatable = false)
    private String auditorAccountId;

    @Column(name = "notes", columnDefinition = "TEXT", updatable = false)
    private String notes;

    @Column(name = "evidence_attachment_url", length = 500, updatable = false)
    private String evidenceAttachmentUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (auditDate == null) {
            auditDate = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }
}
