package com.familyfund.contribution.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public summary of a member's individual contribution detail.
 * Exposed for cross-module queries (e.g. US-11 recursive aggregation and reporting).
 */
public record MemberContributionDetailDto(
        String contributionId,
        String memberId,
        BigDecimal recordedAmount,
        ContributionType contributionType,
        Instant contributionDate,
        String eventId
) {
}
