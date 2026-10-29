package com.familyfund.contribution.internal.dto;

import java.math.BigDecimal;

/**
 * Detailed allocation info returned in the electronic receipt.
 */
public record MemberAllocationResponse(
        String memberId,
        String memberName,
        BigDecimal recordedAmount
) {
}
