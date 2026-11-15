package com.familyfund.genealogy.api.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public DTO representing a hierarchical node in the family branch subtree
 * returned by the Genealogy module's Recursive CTE query (US-11).
 */
public record BranchHierarchyNodeDto(
        String branchId,
        String clanId,
        String branchName,
        Integer generationLevel,
        String parentBranchId,
        String representativeId,
        String representativeName,
        String description,
        int directMemberCount,
        List<BranchHierarchyNodeDto> childBranches
) {
    public BranchHierarchyNodeDto {
        childBranches = (childBranches != null) ? List.copyOf(childBranches) : Collections.emptyList();
    }

    /**
     * Tính tổng đóng góp đệ quy trên nút phân cấp nhánh (đồng bộ với thuật toán trên {@code FamilyBranch}).
     */
    public BigDecimal tinhTongDongGopDeQuy(String maSuKien, Map<String, BigDecimal> directBranchContributions) {
        return tinhTongDongGopDeQuyInternal(directBranchContributions, new HashSet<>());
    }

    private BigDecimal tinhTongDongGopDeQuyInternal(
            Map<String, BigDecimal> directBranchContributions,
            Set<String> visited
    ) {
        if (branchId == null || !visited.add(branchId)) {
            return BigDecimal.ZERO;
        }
        BigDecimal direct = (directBranchContributions != null)
                ? directBranchContributions.getOrDefault(branchId, BigDecimal.ZERO)
                : BigDecimal.ZERO;
        if (direct == null) {
            direct = BigDecimal.ZERO;
        }
        BigDecimal childrenSum = BigDecimal.ZERO;
        for (BranchHierarchyNodeDto child : childBranches) {
            if (child != null) {
                childrenSum = childrenSum.add(child.tinhTongDongGopDeQuyInternal(directBranchContributions, visited));
            }
        }
        return direct.add(childrenSum);
    }
}
