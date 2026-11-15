package com.familyfund.contribution.internal.entity;

import com.familyfund.contribution.api.dto.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity mapping table `cash_contributions`.
 * Specialization for contributions made via Cash or Bank Transfer with a unique receipt number.
 * Expanded with reconciliation fields for bank transfers (US-50).
 */
@Entity
@Table(name = "cash_contributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashContribution {

    @Id
    @Column(name = "contribution_id", length = 20, nullable = false)
    private String contributionId;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20, nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "receipt_number", length = 30, nullable = false, unique = true)
    private String receiptNumber;

    @Column(name = "bank_transaction_code", length = 100)
    private String bankTransactionCode;

    @Column(name = "transfer_proof_url", length = 500)
    private String transferProofUrl;

    @Column(name = "is_reconciled", nullable = false)
    @Builder.Default
    private Boolean isReconciled = false;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "reconciled_by", length = 20)
    private String reconciledBy;
}
