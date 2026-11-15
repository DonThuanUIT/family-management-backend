package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.response.BranchRecursiveContributionResponse;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository.BranchDirectContributionProjection;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.BranchHierarchyNodeDto;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit Tests cho {@link BranchContributionServiceImpl} kiểm thử tầng Service của US-11.
 */
@ExtendWith(MockitoExtension.class)
class BranchContributionServiceTest {

    private static final String CLAN_ID = "CLAN_DEFAULT";
    private static final String EVENT_ID = "EVT-GIO-TO-2026";

    @Mock
    private ContributionMemberDetailRepository contributionMemberDetailRepository;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private EventQueryService eventQueryService;

    @InjectMocks
    private BranchContributionServiceImpl branchContributionService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("US-11: getRecursiveBranchContributionSummary tách bạch đóng góp trực tiếp, nhánh con, tiền mặt và hiện vật")
    void shouldReturnHierarchicalSummarySeparatingDirectAndDescendants() {
        when(eventQueryService.findEventSummary(EVENT_ID)).thenReturn(Optional.of(
                new EventSummaryDto(EVENT_ID, CLAN_ID, "Đại Lễ Giỗ Tổ 2026", EventStatus.Ongoing, Instant.now())
        ));

        BranchHierarchyNodeDto child1 = new BranchHierarchyNodeDto(
                "BR-CHI-1", CLAN_ID, "Chi 1", 2, "BR-ROOT", "MEM-C1", "Nguyễn Văn An", "Chi trưởng", 8, List.of()
        );
        BranchHierarchyNodeDto child2 = new BranchHierarchyNodeDto(
                "BR-CHI-2", CLAN_ID, "Chi 2", 2, "BR-ROOT", "MEM-C2", "Nguyễn Văn Bình", "Chi thứ", 6, List.of()
        );
        BranchHierarchyNodeDto rootNode = new BranchHierarchyNodeDto(
                "BR-ROOT", CLAN_ID, "Đại Tôn Nguyễn Tộc", 1, null, "MEM-ROOT", "Nguyễn Trọng Hoàng", "Đại Tôn", 4,
                List.of(child1, child2)
        );

        when(genealogyQueryService.findBranchSubtree(CLAN_ID, "BR-ROOT")).thenReturn(Optional.of(rootNode));

        BranchDirectContributionProjection pRoot = projection(
                "BR-ROOT", "10000000.00", "10000000.00", "0.00", 1, 1
        );
        BranchDirectContributionProjection pChi1 = projection(
                "BR-CHI-1", "23000000.00", "5000000.00", "18000000.00", 2, 2
        );
        BranchDirectContributionProjection pChi2 = projection(
                "BR-CHI-2", "7000000.00", "2000000.00", "5000000.00", 1, 2
        );

        when(contributionMemberDetailRepository.aggregateDirectContributionsByBranchSubtree(
                CLAN_ID, "BR-ROOT", EVENT_ID, 2026
        )).thenReturn(List.of(pRoot, pChi1, pChi2));

        when(genealogyQueryService.tinhTongDongGopDeQuy(eq(CLAN_ID), eq("BR-ROOT"), eq(EVENT_ID), anyMap()))
                .thenReturn(new BigDecimal("40000000.00"));

        BranchRecursiveContributionResponse summary = branchContributionService.getRecursiveBranchContributionSummary(
                "BR-ROOT", EVENT_ID, 2026
        );

        assertNotNull(summary);
        assertEquals("BR-ROOT", summary.branchId());
        assertEquals(0, new BigDecimal("10000000.00").compareTo(summary.directTotalAmount()));
        assertEquals(0, new BigDecimal("30000000.00").compareTo(summary.descendantsTotalAmount()));
        assertEquals(0, new BigDecimal("40000000.00").compareTo(summary.totalRecursiveAmount()));
        assertEquals(0, new BigDecimal("17000000.00").compareTo(summary.totalRecursiveCashAmount()));
        assertEquals(0, new BigDecimal("23000000.00").compareTo(summary.totalRecursiveInKindAmount()));
        assertEquals(4, summary.directMemberCount());
        assertEquals(18, summary.totalRecursiveMemberCount());
        assertEquals(1, summary.directContributingMemberCount());
        assertEquals(4, summary.totalRecursiveContributingMemberCount());
        assertEquals("Bốn mươi triệu đồng chẵn", summary.totalRecursiveAmountInWords());
        assertEquals(2, summary.childBranches().size());
    }

    @Test
    @DisplayName("US-11: Từ chối truy vấn khi sự kiện thuộc về dòng họ khác (Multi-tenancy isolation)")
    void shouldRejectWhenEventBelongsToAnotherClan() {
        when(eventQueryService.findEventSummary("EVT-OTHER")).thenReturn(Optional.of(
                new EventSummaryDto("EVT-OTHER", "CLAN_OTHER", "Sự kiện họ khác", EventStatus.Ongoing, Instant.now())
        ));

        assertThrows(BusinessRuleException.class, () ->
                branchContributionService.tinhTongDongGopDeQuy("BR-ROOT", "EVT-OTHER")
        );
    }

    @Test
    @DisplayName("US-11: Báo lỗi 404 khi nhánh không tồn tại trong dòng họ hiện tại")
    void shouldThrowResourceNotFoundWhenBranchDoesNotExist() {
        when(genealogyQueryService.existsBranch(CLAN_ID, "BR-UNKNOWN")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                branchContributionService.tinhTongDongGopDeQuy("BR-UNKNOWN", null)
        );
    }

    private static BranchDirectContributionProjection projection(
            String branchId,
            String total,
            String cash,
            String inKind,
            int memberCount,
            int contributionCount
    ) {
        return new BranchDirectContributionProjection() {
            @Override
            public String getBranchId() {
                return branchId;
            }

            @Override
            public BigDecimal getDirectTotalAmount() {
                return new BigDecimal(total);
            }

            @Override
            public BigDecimal getDirectCashAmount() {
                return new BigDecimal(cash);
            }

            @Override
            public BigDecimal getDirectInKindAmount() {
                return new BigDecimal(inKind);
            }

            @Override
            public Integer getContributingMemberCount() {
                return memberCount;
            }

            @Override
            public Integer getContributionCount() {
                return contributionCount;
            }
        };
    }
}
