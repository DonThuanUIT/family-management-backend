package com.familyfund.contribution.internal.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Entity mapping table `contribution_member_details`.
 * Records individual member attribution for contributions (ground truth for recursive aggregation in US-11).
 */
@Entity
@Table(name = "contribution_member_details")
@IdClass(ContributionMemberDetailId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributionMemberDetail {

    @Id
    @Column(name = "contribution_id", length = 20, nullable = false)
    private String contributionId;

    @Id
    @Column(name = "member_id", length = 20, nullable = false)
    private String memberId;

    @Column(name = "recorded_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal recordedAmount;
}
