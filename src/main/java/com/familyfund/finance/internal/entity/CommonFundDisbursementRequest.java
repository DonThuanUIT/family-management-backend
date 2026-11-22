package com.familyfund.finance.internal.entity;

import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
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
 * Entity mapping table {@code common_fund_disbursement_requests} (US-60).
 */
@Entity
@Table(name = "common_fund_disbursement_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonFundDisbursementRequest {

    @Id
    @Column(name = "request_id", length = 20, nullable = false, updatable = false)
    private String requestId;

    @Column(name = "clan_id", length = 20, nullable = false, updatable = false)
    private String clanId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 40, nullable = false)
    private CommonFundExpenseCategory category;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "beneficiary_name", length = 255)
    private String beneficiaryName;

    @Column(name = "beneficiary_member_id", length = 20)
    private String beneficiaryMemberId;

    @Column(name = "purpose", columnDefinition = "TEXT", nullable = false)
    private String purpose;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @Column(name = "proposed_by_account_id", length = 20, nullable = false, updatable = false)
    private String proposedByAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20, nullable = false)
    @Builder.Default
    private CommonFundDisbursementStatus approvalStatus = CommonFundDisbursementStatus.Pending;

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
            approvalStatus = CommonFundDisbursementStatus.Pending;
        }
    }
}
