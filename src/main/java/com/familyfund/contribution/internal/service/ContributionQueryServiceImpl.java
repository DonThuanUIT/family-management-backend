package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.ContributionQueryService;
import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.api.dto.MemberContributionDetailDto;
import com.familyfund.contribution.internal.entity.Contribution;
import com.familyfund.contribution.internal.entity.ContributionMemberDetail;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository;
import com.familyfund.contribution.internal.repository.ContributionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContributionQueryServiceImpl implements ContributionQueryService {

    private final ContributionMemberDetailRepository detailRepository;
    private final ContributionRepository contributionRepository;

    @Override
    public List<MemberContributionDetailDto> findDetailsByMemberIds(Collection<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<MemberContributionDetailDto> result = new ArrayList<>();
        for (String memberId : memberIds) {
            List<ContributionMemberDetail> details = detailRepository.findAllByMemberId(memberId);
            for (ContributionMemberDetail detail : details) {
                Contribution contribution = contributionRepository.findById(detail.getContributionId()).orElse(null);
                if (contribution != null) {
                    result.add(new MemberContributionDetailDto(
                            detail.getContributionId(),
                            detail.getMemberId(),
                            detail.getRecordedAmount(),
                            contribution.getContributionType(),
                            contribution.getContributionDate(),
                            contribution.getEventId()
                    ));
                }
            }
        }

        return result;
    }

    @Override
    public BigDecimal getTotalContributedByMember(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return BigDecimal.ZERO;
        }

        List<ContributionMemberDetail> details = detailRepository.findAllByMemberId(memberId);
        return details.stream()
                .map(ContributionMemberDetail::getRecordedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
