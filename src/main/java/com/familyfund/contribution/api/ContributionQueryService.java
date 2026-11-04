package com.familyfund.contribution.api;

import com.familyfund.contribution.api.dto.MemberContributionDetailDto;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * Public query contract provided by the Contribution module for cross-module consumption (e.g. US-11).
 */
public interface ContributionQueryService {

    /**
     * Finds all contribution member details for the specified member IDs.
     * Serves as input for recursive aggregation in US-11.
     *
     * @param memberIds collection of member IDs
     * @return list of member contribution details
     */
    List<MemberContributionDetailDto> findDetailsByMemberIds(Collection<String> memberIds);

    /**
     * Calculates the total monetary contributions made by a specific member.
     *
     * @param memberId ID of the member
     * @return total contributed amount, or zero if none
     */
    BigDecimal getTotalContributedByMember(String memberId);
}
