package com.familyfund.contribution.internal.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Summary and itemized list of contribution history for a member/household (US-49).
 */
public record MemberContributionHistoryResponse(
        String memberId,
        String memberName,
        String branchId,
        String branchName,
        String clanId,
        BigDecimal totalPersonalContributed,
        BigDecimal totalAllocated,
        List<MemberContributionHistoryItemResponse> items
) {
}
