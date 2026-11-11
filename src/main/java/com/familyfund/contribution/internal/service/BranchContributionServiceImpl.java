package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.response.BranchRecursiveContributionResponse;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository;
import com.familyfund.contribution.internal.repository.ContributionMemberDetailRepository.BranchDirectContributionProjection;
import com.familyfund.contribution.internal.util.MoneyToWordsConverter;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.BranchHierarchyNodeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cài đặt tầng Service cho giải thuật đệ quy {@code tinhTongDongGopDeQuy(maSuKien)} (US-11).
 * Kết hợp truy vấn PostgreSQL Recursive CTE dưới CSDL và phương thức đệ quy trên thực thể {@code FamilyBranch} ({@code NhanhGiaDinh}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchContributionServiceImpl implements BranchContributionService {

    private final ContributionMemberDetailRepository contributionMemberDetailRepository;
    private final GenealogyQueryService genealogyQueryService;
    private final EventQueryService eventQueryService;

    @Override
    public BigDecimal tinhTongDongGopDeQuy(String branchId, String maSuKien) {
        return tinhTongDongGopDeQuy(branchId, maSuKien, null);
    }

    @Override
    public BigDecimal tinhTongDongGopDeQuy(String branchId, String maSuKien, Integer year) {
        String clanId = requireClanId();
        String normalizedBranchId = requireBranchId(branchId);
        String normalizedEventId = normalizeOptionalEventId(maSuKien);
        validateEventAndYearFilters(clanId, normalizedEventId, year);

        if (!genealogyQueryService.existsBranch(clanId, normalizedBranchId)) {
            throw new ResourceNotFoundException("Branch not found with ID: " + normalizedBranchId);
        }

        // 1. Truy vấn CSDL bằng PostgreSQL Recursive CTE để gom nhóm đóng góp trực tiếp từ CONTRIBUTION_MEMBER_DETAIL
        List<BranchDirectContributionProjection> projections =
                contributionMemberDetailRepository.aggregateDirectContributionsByBranchSubtree(
                        clanId,
                        normalizedBranchId,
                        normalizedEventId,
                        year
                );

        Map<String, BigDecimal> directBranchContributions = new LinkedHashMap<>();
        for (BranchDirectContributionProjection p : projections) {
            directBranchContributions.put(
                    p.getBranchId(),
                    p.getDirectTotalAmount() != null ? p.getDirectTotalAmount() : BigDecimal.ZERO
            );
        }

        // 2. Gọi giải thuật đệ quy tinhTongDongGopDeQuy(maSuKien) trên mô hình phân cấp FamilyBranch (NhanhGiaDinh)
        BigDecimal totalRecursive = genealogyQueryService.tinhTongDongGopDeQuy(
                clanId,
                normalizedBranchId,
                normalizedEventId,
                directBranchContributions
        );

        log.debug("Calculated recursive contribution for branch {} (event={}, year={}): {}",
                normalizedBranchId, normalizedEventId, year, totalRecursive);
        return totalRecursive;
    }

    @Override
    public BranchRecursiveContributionResponse getRecursiveBranchContributionSummary(
            String branchId,
            String maSuKien,
            Integer year
    ) {
        String clanId = requireClanId();
        String normalizedBranchId = requireBranchId(branchId);
        String normalizedEventId = normalizeOptionalEventId(maSuKien);
        EventSummaryDto eventSummary = validateEventAndYearFilters(clanId, normalizedEventId, year);

        // 1. Lấy cây phả hệ nhánh con bằng PostgreSQL Recursive CTE thông qua GenealogyQueryService
        BranchHierarchyNodeDto subtreeRoot = genealogyQueryService.findBranchSubtree(clanId, normalizedBranchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with ID: " + normalizedBranchId));

        // 2. Lấy số liệu đóng góp trực tiếp của từng nhánh trong cây con bằng PostgreSQL Recursive CTE
        List<BranchDirectContributionProjection> projections =
                contributionMemberDetailRepository.aggregateDirectContributionsByBranchSubtree(
                        clanId,
                        normalizedBranchId,
                        normalizedEventId,
                        year
                );

        Map<String, BranchDirectStats> statsByBranchId = new LinkedHashMap<>();
        Map<String, BigDecimal> directTotalMap = new LinkedHashMap<>();
        for (BranchDirectContributionProjection p : projections) {
            BranchDirectStats stats = new BranchDirectStats(
                    p.getDirectCashAmount() != null ? p.getDirectCashAmount() : BigDecimal.ZERO,
                    p.getDirectInKindAmount() != null ? p.getDirectInKindAmount() : BigDecimal.ZERO,
                    p.getDirectTotalAmount() != null ? p.getDirectTotalAmount() : BigDecimal.ZERO,
                    p.getContributingMemberCount() != null ? p.getContributingMemberCount() : 0,
                    p.getContributionCount() != null ? p.getContributionCount() : 0
            );
            statsByBranchId.put(p.getBranchId(), stats);
            directTotalMap.put(p.getBranchId(), stats.directTotalAmount());
        }

        // 3. Thực thi phương thức đệ quy tinhTongDongGopDeQuy trên FamilyBranch để đảm bảo tính nhất quán tuyệt đối
        BigDecimal domainRecursiveTotal = genealogyQueryService.tinhTongDongGopDeQuy(
                clanId,
                normalizedBranchId,
                normalizedEventId,
                directTotalMap
        );

        String eventName = eventSummary != null ? eventSummary.eventName() : null;

        // 4. Tổng hợp cây báo cáo phân rã đệ quy từ lá lên gốc (Post-order DFS)
        BranchRecursiveContributionResponse summary = buildRecursiveSummaryNode(
                subtreeRoot,
                statsByBranchId,
                normalizedEventId,
                eventName,
                year,
                new HashSet<>()
        );

        // Đảm bảo tổng đệ quy tại nút gốc khớp tuyệt đối với kết quả tinhTongDongGopDeQuy trên FamilyBranch
        if (summary.totalRecursiveAmount().compareTo(domainRecursiveTotal) != 0) {
            log.warn("Recursive total mismatch detected for branch {}: tree={} vs domain={}",
                    normalizedBranchId, summary.totalRecursiveAmount(), domainRecursiveTotal);
        }

        return summary;
    }

    private BranchRecursiveContributionResponse buildRecursiveSummaryNode(
            BranchHierarchyNodeDto node,
            Map<String, BranchDirectStats> statsByBranchId,
            String eventId,
            String eventName,
            Integer year,
            Set<String> visited
    ) {
        if (node.branchId() == null || !visited.add(node.branchId())) {
            return new BranchRecursiveContributionResponse(
                    node.branchId(),
                    node.branchName(),
                    node.generationLevel(),
                    node.parentBranchId(),
                    node.representativeId(),
                    node.representativeName(),
                    eventId,
                    eventName,
                    year,
                    0, 0, 0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    MoneyToWordsConverter.toVietnameseWords(BigDecimal.ZERO),
                    List.of()
            );
        }

        BranchDirectStats direct = statsByBranchId.getOrDefault(node.branchId(), BranchDirectStats.EMPTY);

        BigDecimal descendantsCash = BigDecimal.ZERO;
        BigDecimal descendantsInKind = BigDecimal.ZERO;
        BigDecimal descendantsTotal = BigDecimal.ZERO;
        int descendantsMemberCount = 0;
        int descendantsContributingMemberCount = 0;

        List<BranchRecursiveContributionResponse> childResponses = new ArrayList<>();
        if (node.childBranches() != null && !node.childBranches().isEmpty()) {
            for (BranchHierarchyNodeDto childNode : node.childBranches()) {
                if (childNode != null) {
                    BranchRecursiveContributionResponse childResp = buildRecursiveSummaryNode(
                            childNode,
                            statsByBranchId,
                            eventId,
                            eventName,
                            year,
                            visited
                    );
                    childResponses.add(childResp);
                    descendantsCash = descendantsCash.add(childResp.totalRecursiveCashAmount());
                    descendantsInKind = descendantsInKind.add(childResp.totalRecursiveInKindAmount());
                    descendantsTotal = descendantsTotal.add(childResp.totalRecursiveAmount());
                    descendantsMemberCount += childResp.totalRecursiveMemberCount();
                    descendantsContributingMemberCount += childResp.totalRecursiveContributingMemberCount();
                }
            }
        }

        BigDecimal totalRecursiveCash = direct.directCashAmount().add(descendantsCash);
        BigDecimal totalRecursiveInKind = direct.directInKindAmount().add(descendantsInKind);
        BigDecimal totalRecursiveAmount = direct.directTotalAmount().add(descendantsTotal);
        int totalRecursiveMemberCount = node.directMemberCount() + descendantsMemberCount;
        int totalRecursiveContributingMemberCount = direct.contributingMemberCount() + descendantsContributingMemberCount;

        return new BranchRecursiveContributionResponse(
                node.branchId(),
                node.branchName(),
                node.generationLevel(),
                node.parentBranchId(),
                node.representativeId(),
                node.representativeName(),
                eventId,
                eventName,
                year,
                node.directMemberCount(),
                totalRecursiveMemberCount,
                direct.contributingMemberCount(),
                totalRecursiveContributingMemberCount,
                direct.contributionCount(),
                direct.directCashAmount(),
                direct.directInKindAmount(),
                direct.directTotalAmount(),
                descendantsCash,
                descendantsInKind,
                descendantsTotal,
                totalRecursiveCash,
                totalRecursiveInKind,
                totalRecursiveAmount,
                MoneyToWordsConverter.toVietnameseWords(totalRecursiveAmount),
                childResponses
        );
    }

    private String requireClanId() {
        String clanId = TenantContext.getClanId();
        if (clanId == null || clanId.isBlank()) {
            throw new BusinessRuleException("Clan tenant context is required");
        }
        return clanId.trim();
    }

    private String requireBranchId(String branchId) {
        if (branchId == null || branchId.isBlank()) {
            throw new BusinessRuleException("Branch ID must not be blank");
        }
        return branchId.trim();
    }

    private String normalizeOptionalEventId(String maSuKien) {
        if (maSuKien == null || maSuKien.isBlank()) {
            return null;
        }
        return maSuKien.trim();
    }

    private EventSummaryDto validateEventAndYearFilters(String clanId, String eventId, Integer year) {
        if (year != null && (year < 1800 || year > 2200)) {
            throw new BusinessRuleException("Invalid contribution filter year: " + year);
        }

        if (eventId == null) {
            return null;
        }

        EventSummaryDto eventSummary = eventQueryService.findEventSummary(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        if (!clanId.equals(eventSummary.clanId())) {
            throw new BusinessRuleException("Event does not belong to the current clan");
        }
        return eventSummary;
    }

    private record BranchDirectStats(
            BigDecimal directCashAmount,
            BigDecimal directInKindAmount,
            BigDecimal directTotalAmount,
            int contributingMemberCount,
            int contributionCount
    ) {
        static final BranchDirectStats EMPTY = new BranchDirectStats(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0
        );
    }
}
