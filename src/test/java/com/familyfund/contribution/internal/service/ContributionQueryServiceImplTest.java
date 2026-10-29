package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.api.dto.MemberContributionDetailDto;
import com.familyfund.contribution.internal.entity.Contribution;
import com.familyfund.contribution.internal.entity.ContributionMemberDetail;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository;
import com.familyfund.contribution.internal.repository.ContributionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContributionQueryServiceImplTest {

    @Mock
    private ContributionMemberDetailRepository detailRepository;

    @Mock
    private ContributionRepository contributionRepository;

    @InjectMocks
    private ContributionQueryServiceImpl queryService;

    @Test
    @DisplayName("Should return empty list when querying details with null or empty memberIds")
    void findDetailsByMemberIds_emptyInput() {
        assertThat(queryService.findDetailsByMemberIds(null)).isEmpty();
        assertThat(queryService.findDetailsByMemberIds(Collections.emptyList())).isEmpty();
    }

    @Test
    @DisplayName("Should find and assemble member contribution details correctly")
    void findDetailsByMemberIds_success() {
        String memberId = "MEM_001";
        String ctbId = "CTB_001";
        Instant now = Instant.now();

        ContributionMemberDetail detail = ContributionMemberDetail.builder()
                .contributionId(ctbId)
                .memberId(memberId)
                .recordedAmount(BigDecimal.valueOf(500000))
                .build();

        Contribution contribution = Contribution.builder()
                .contributionId(ctbId)
                .eventId("EVT_001")
                .payerMemberId(memberId)
                .receiverAccountId("ACC_001")
                .contributionType(ContributionType.Cash)
                .contributionDate(now)
                .totalMonetaryValue(BigDecimal.valueOf(500000))
                .build();

        when(detailRepository.findAllByMemberId(memberId)).thenReturn(List.of(detail));
        when(contributionRepository.findById(ctbId)).thenReturn(Optional.of(contribution));

        List<MemberContributionDetailDto> result = queryService.findDetailsByMemberIds(List.of(memberId));

        assertThat(result).hasSize(1);
        MemberContributionDetailDto dto = result.get(0);
        assertThat(dto.memberId()).isEqualTo(memberId);
        assertThat(dto.contributionId()).isEqualTo(ctbId);
        assertThat(dto.recordedAmount()).isEqualTo(BigDecimal.valueOf(500000));
        assertThat(dto.contributionType()).isEqualTo(ContributionType.Cash);
        assertThat(dto.eventId()).isEqualTo("EVT_001");
    }

    @Test
    @DisplayName("Should calculate total contributed amount by member correctly")
    void getTotalContributedByMember_success() {
        String memberId = "MEM_001";
        ContributionMemberDetail d1 = ContributionMemberDetail.builder()
                .contributionId("CTB_1").memberId(memberId).recordedAmount(BigDecimal.valueOf(200000)).build();
        ContributionMemberDetail d2 = ContributionMemberDetail.builder()
                .contributionId("CTB_2").memberId(memberId).recordedAmount(BigDecimal.valueOf(300000)).build();

        when(detailRepository.findAllByMemberId(memberId)).thenReturn(List.of(d1, d2));

        BigDecimal total = queryService.getTotalContributedByMember(memberId);

        assertThat(total).isEqualTo(BigDecimal.valueOf(500000));
    }
}
