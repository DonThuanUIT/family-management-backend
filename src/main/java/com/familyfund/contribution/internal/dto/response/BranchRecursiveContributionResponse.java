package com.familyfund.contribution.internal.dto.response;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Response DTO biểu diễn kết quả tính tổng mức đóng góp đệ quy {@code tinhTongDongGopDeQuy(maSuKien)}
 * của một nhánh gia phả bất kỳ cùng toàn bộ cây nhánh con bên dưới (US-11).
 *
 * <p>Hiển thị tách bạch:
 * <ul>
 *   <li>Đóng góp trực tiếp của các thành viên thuộc nhánh đang xét ({@code directTotalAmount})</li>
 *   <li>Tổng đóng góp cộng dồn từ tất cả các nhánh con bên dưới ({@code descendantsTotalAmount})</li>
 *   <li>Tổng mức đóng góp đệ quy ({@code totalRecursiveAmount = directTotalAmount + descendantsTotalAmount})</li>
 * </ul>
 */
public record BranchRecursiveContributionResponse(
        String branchId,
        String branchName,
        Integer generationLevel,
        String parentBranchId,
        String representativeId,
        String representativeName,
        String eventId,
        String eventName,
        Integer year,
        int directMemberCount,
        int totalRecursiveMemberCount,
        int directContributingMemberCount,
        int totalRecursiveContributingMemberCount,
        int directContributionCount,
        BigDecimal directCashAmount,
        BigDecimal directInKindAmount,
        BigDecimal directTotalAmount,
        BigDecimal descendantsCashAmount,
        BigDecimal descendantsInKindAmount,
        BigDecimal descendantsTotalAmount,
        BigDecimal totalRecursiveCashAmount,
        BigDecimal totalRecursiveInKindAmount,
        BigDecimal totalRecursiveAmount,
        String totalRecursiveAmountInWords,
        List<BranchRecursiveContributionResponse> childBranches
) {
    public BranchRecursiveContributionResponse {
        childBranches = (childBranches != null) ? List.copyOf(childBranches) : Collections.emptyList();
    }
}
