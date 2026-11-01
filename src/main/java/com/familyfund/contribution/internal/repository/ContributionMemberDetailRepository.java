package com.familyfund.contribution.internal.repository;

import com.familyfund.contribution.internal.entity.ContributionMemberDetail;
import com.familyfund.contribution.internal.entity.ContributionMemberDetailId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContributionMemberDetailRepository extends JpaRepository<ContributionMemberDetail, ContributionMemberDetailId> {

    List<ContributionMemberDetail> findAllByContributionId(String contributionId);

    List<ContributionMemberDetail> findAllByContributionIdIn(List<String> contributionIds);

    List<ContributionMemberDetail> findAllByMemberId(String memberId);

    void deleteAllByContributionId(String contributionId);
}
