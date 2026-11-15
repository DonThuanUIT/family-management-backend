package com.familyfund.finance.internal.entity;

import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity mapping table `advance_vouchers` (US-46).
 */
@Entity
@Table(name = "advance_vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdvanceVoucher {

    @Id
    @Column(name = "advance_voucher_id", length = 20, nullable = false)
    private String advanceVoucherId;

    @Column(name = "task_id", length = 20, nullable = false)
    private String taskId;

    @Column(name = "clan_id", length = 20)
    private String clanId;

    @Column(name = "recipient_member_id", length = 20, nullable = false)
    private String recipientMemberId;

    @Column(name = "advance_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal advanceAmount;

    @Column(name = "received_date", nullable = false)
    private Instant receivedDate;

    @Column(name = "settlement_deadline", nullable = false)
    private Instant settlementDeadline;

    @Column(name = "commitment_notes", columnDefinition = "TEXT")
    private String commitmentNotes;

    @Column(name = "created_by_account_id", length = 20, nullable = false)
    private String createdByAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25, nullable = false)
    @Builder.Default
    private AdvanceVoucherStatus status = AdvanceVoucherStatus.ChoGiaoTien;

    @Column(name = "treasurer_account_id", length = 20)
    private String treasurerAccountId;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "disbursement_notes", columnDefinition = "TEXT")
    private String disbursementNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = AdvanceVoucherStatus.ChoGiaoTien;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
