package com.familyfund.contribution.internal.dto.response;

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
