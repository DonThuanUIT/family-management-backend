package com.familyfund.contribution.internal.dto;

import com.familyfund.contribution.api.dto.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Electronic receipt response containing payment, payer, allocation details and words representation.
 */
public record ElectronicReceiptResponse(
        String receiptNumber,
        String contributionId,
        String eventId,
        String eventName,
        String payerMemberId,
        String payerMemberName,
        String receiverAccountId,
        BigDecimal amount,
        String amountInWords,
        PaymentMethod paymentMethod,
        Instant contributionDate,
        List<MemberAllocationResponse> allocations
) {
}
