package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.api.dto.InKindProcessingStatus;
import com.familyfund.contribution.internal.dto.response.EventPendingInKindCheckResponse;
import com.familyfund.contribution.internal.dto.response.InKindContributionDetailResponse;
import com.familyfund.contribution.internal.entity.Contribution;
import com.familyfund.contribution.internal.entity.InKindContribution;
import com.familyfund.contribution.internal.repository.*;
import com.familyfund.contribution.internal.util.AssetIdGenerator;
import com.familyfund.contribution.internal.util.ContributionIdGenerator;
import com.familyfund.contribution.internal.util.ReceiptNumberGenerator;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InKindPostEventStatusServiceImplTest {

    @Mock
    private InKindContributionRepository inKindContributionRepository;

    @Mock
    private ContributionRepository contributionRepository;

    @Mock
    private EventItemBenchmarkRepository eventItemBenchmarkRepository;

    @Mock
    private ClanAssetRepository clanAssetRepository;

    @Mock
    private ContributionMemberDetailRepository contributionMemberDetailRepository;

    @Mock
    private AssetIdGenerator assetIdGenerator;

    @Mock
    private ContributionIdGenerator contributionIdGenerator;

    @Mock
    private ReceiptNumberGenerator receiptNumberGenerator;

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private InKindContributionServiceImpl inKindContributionService;

    private final String eventId = "EVT_001";
    private final String clanId = "CLAN_001";

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(clanId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("checkPendingInKindContributions - alerts when there are pending items")
    void checkPendingInKindContributions_hasPending_returnsWarning() {
        when(currentUserProvider.hasAuthority(any())).thenReturn(true);
        EventSummaryDto eventSummary = new EventSummaryDto(eventId, clanId, "Đại Lễ Giỗ Tổ", EventStatus.Draft, Instant.now());
        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(eventSummary));

        InKindContribution ikc = InKindContribution.builder()
                .contributionId("CTB_IK_01")
                .receiptNumber("PT-20261109-00001")
                .itemName("Heo quay")
                .quantity(new BigDecimal("1"))
                .unit("con")
                .convertedMonetaryValue(new BigDecimal("3500000.00"))
                .processingStatus(InKindProcessingStatus.Pending)
                .build();

        Contribution c = Contribution.builder()
                .contributionId("CTB_IK_01")
                .eventId(eventId)
                .payerMemberId("MEM_01")
                .receiverAccountId("ACC_01")
                .totalMonetaryValue(new BigDecimal("3500000.00"))
                .contributionDate(Instant.now())
                .build();

        when(inKindContributionRepository.findByEventIdAndStatus(eventId, InKindProcessingStatus.Pending))
                .thenReturn(List.of(ikc));
        when(contributionRepository.findAllById(any())).thenReturn(List.of(c));
        when(genealogyQueryService.findMemberSummary("MEM_01"))
                .thenReturn(Optional.of(new MemberSummaryDto("MEM_01", "Nguyễn Văn A", clanId, "BR_01")));
        when(contributionMemberDetailRepository.findAllByContributionIdIn(any())).thenReturn(Collections.emptyList());

        EventPendingInKindCheckResponse response = inKindContributionService.checkPendingInKindContributions(eventId);

        assertThat(response).isNotNull();
        assertThat(response.hasPendingItems()).isTrue();
        assertThat(response.pendingCount()).isEqualTo(1);
        assertThat(response.warningMessage()).contains("Còn 1 hiện vật");
        assertThat(response.pendingItems()).hasSize(1);
        assertThat(response.pendingItems().get(0).itemName()).isEqualTo("Heo quay");
    }

    @Test
    @DisplayName("processLifecycle - throws AccessDeniedException if regular member without event role")
    void processLifecycle_unauthorizedMember_throwsAccessDenied() {
        Contribution c = Contribution.builder()
                .contributionId("CTB_IK_01")
                .eventId(eventId)
                .build();
        InKindContribution ikc = InKindContribution.builder()
                .contributionId("CTB_IK_01")
                .processingStatus(InKindProcessingStatus.Pending)
                .build();

        when(inKindContributionRepository.findById("CTB_IK_01")).thenReturn(Optional.of(ikc));
        when(contributionRepository.findById("CTB_IK_01")).thenReturn(Optional.of(c));
        when(eventQueryService.findEventSummary(eventId)).thenReturn(Optional.of(new EventSummaryDto(eventId, clanId, "Lễ", EventStatus.Draft, Instant.now())));
        when(currentUserProvider.hasAuthority(any())).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("REGULAR_MEM"));
        when(eventQueryService.getMemberRolesInEvent(eventId, "REGULAR_MEM")).thenReturn(Set.of());

        ProcessInKindLifecycleRequest request = new ProcessInKindLifecycleRequest(
                InKindProcessingStatus.Consumed, "Đã thụ lộc", null, null, null, null, null
        );

        assertThatThrownBy(() -> inKindContributionService.processLifecycle("CTB_IK_01", request))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Only Clan Head, Admin, Head of Organizer, or Event Treasurer");
    }
}
