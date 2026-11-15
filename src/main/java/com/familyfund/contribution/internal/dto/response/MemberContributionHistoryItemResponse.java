package com.familyfund.contribution.internal.dto.response;

import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.api.dto.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single item representation in a member's or household's contribution history (US-49).
 */
public record MemberContributionHistoryItemResponse(
        String contributionId,
        String receiptNumber,
        String eventId,
        String eventName,
        String payerMemberId,
        String payerMemberName,
        ContributionType contributionType,
        PaymentMethod paymentMethod,
        String itemName,
        BigDecimal quantity,
        String unit,
        BigDecimal totalMonetaryValue,
        BigDecimal myAllocatedAmount,
        String status,
        Instant contributionDate
) {
}
