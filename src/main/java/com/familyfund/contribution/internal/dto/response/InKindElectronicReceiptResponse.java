package com.familyfund.contribution.internal.dto.response;

import com.familyfund.contribution.api.dto.ConversionMethod;
import com.familyfund.contribution.api.dto.InKindProcessingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Electronic receipt response for in-kind contributions (US-10, Task #72, #74).
 */
public record InKindElectronicReceiptResponse(
        String receiptNumber,
        String contributionId,
        String eventId,
        String eventName,
        String payerMemberId,
        String payerMemberName,
        String receiverAccountId,
        String itemName,
        BigDecimal quantity,
        String unit,
        ConversionMethod conversionMethod,
        BigDecimal convertedMonetaryValue,
        String convertedMonetaryValueInWords,
        InKindProcessingStatus processingStatus,
        Instant contributionDate,
        List<MemberAllocationResponse> allocations
) {
}
