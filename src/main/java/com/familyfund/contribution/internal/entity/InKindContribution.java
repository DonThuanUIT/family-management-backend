package com.familyfund.contribution.internal.entity;

import com.familyfund.contribution.api.dto.ConversionMethod;
import com.familyfund.contribution.api.dto.InKindProcessingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Entity mapping table `in_kind_contributions`.
 * Specialization for contributions made via In-Kind items (heo quay, mâm quả, đồ thờ...) with value conversion and lifecycle tracking.
 */
@Entity
@Table(name = "in_kind_contributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InKindContribution {

    @Id
    @Column(name = "contribution_id", length = 20, nullable = false)
    private String contributionId;

    @Column(name = "item_name", length = 100, nullable = false)
    private String itemName;

    @Column(name = "quantity", precision = 10, scale = 2, nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", length = 20, nullable = false)
    private String unit;

    @Column(name = "estimated_value", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal estimatedValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversion_method", length = 255, nullable = false)
    private ConversionMethod conversionMethod;

    @Column(name = "converted_monetary_value", precision = 18, scale = 2, nullable = false)
    private BigDecimal convertedMonetaryValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 30, nullable = false)
    @Builder.Default
    private InKindProcessingStatus processingStatus = InKindProcessingStatus.Pending;

    @Column(name = "receipt_number", length = 30, unique = true)
    private String receiptNumber;
}
