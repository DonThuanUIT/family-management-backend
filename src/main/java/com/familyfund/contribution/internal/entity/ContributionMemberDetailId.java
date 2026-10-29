package com.familyfund.contribution.internal.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite primary key for `contribution_member_details` (contribution_id, member_id).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContributionMemberDetailId implements Serializable {
    private String contributionId;
    private String memberId;
}
