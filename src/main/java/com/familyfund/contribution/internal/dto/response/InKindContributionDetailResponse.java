package com.familyfund.contribution.internal.dto.response;

import com.familyfund.contribution.api.dto.ConversionMethod;
import com.familyfund.contribution.api.dto.InKindProcessingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Detailed view of an in-kind contribution including item specifics and lifecycle status.
 */
public record InKindContributionDetailResponse(
        String contributionId,
        String receiptNumber,
        String eventId,
        String eventName,
        String payerMemberId,
        String payerMemberName,
        String receiverAccountId,
        String itemName,
        BigDecimal quantity,
        String unit,
        BigDecimal estimatedValue,
        ConversionMethod conversionMethod,
        BigDecimal convertedMonetaryValue,
        String convertedMonetaryValueInWords,
        InKindProcessingStatus processingStatus,
        Instant contributionDate,
        List<MemberAllocationResponse> allocations
) {
}
