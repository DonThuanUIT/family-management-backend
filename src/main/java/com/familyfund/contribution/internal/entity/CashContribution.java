package com.familyfund.contribution.internal.entity;

import com.familyfund.contribution.api.dto.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Entity mapping table `cash_contributions`.
 * Specialization for contributions made via Cash or Bank Transfer with a unique receipt number.
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
}
