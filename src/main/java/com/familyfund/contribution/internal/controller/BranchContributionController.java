package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.response.BranchRecursiveContributionResponse;
import com.familyfund.contribution.internal.service.BranchContributionService;
import com.familyfund.core.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * REST Controller phục vụ tra cứu tổng mức đóng góp đệ quy của nhánh gia phả (US-11: tinhTongDongGopDeQuy).
 */
@RestController
@RequiredArgsConstructor
public class BranchContributionController {

    private final BranchContributionService branchContributionService;

    /**
     * Tính tổng mức đóng góp đệ quy {@code tinhTongDongGopDeQuy(maSuKien)} của một nhánh bất kỳ
     * (cộng dồn đóng góp trực tiếp của nhánh và toàn bộ các nhánh con bên dưới).
     *
     * @param branchId ID của nhánh gia phả
     * @param eventId  Mã sự kiện lọc (tùy chọn)
     * @param maSuKien Alias tiếng Việt cho mã sự kiện (tùy chọn)
     * @param year     Năm đóng góp lọc (tùy chọn)
     */
    @GetMapping({
            "/api/v1/contributions/branches/{branchId}/recursive-total",
            "/api/v1/branches/{branchId}/contributions/recursive-total"
    })
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BigDecimal>> getRecursiveBranchContributionTotal(
            @PathVariable String branchId,
            @RequestParam(name = "eventId", required = false) String eventId,
            @RequestParam(name = "maSuKien", required = false) String maSuKien,
            @RequestParam(name = "year", required = false) Integer year
    ) {
        String effectiveEventId = (eventId != null && !eventId.isBlank()) ? eventId : maSuKien;
        BigDecimal total = branchContributionService.tinhTongDongGopDeQuy(branchId, effectiveEventId, year);
        return ResponseEntity.ok(ApiResponse.success(
                total,
                "Branch recursive contribution total calculated successfully"
        ));
    }

    /**
     * Tra cứu báo cáo phân rã chi tiết đóng góp đệ quy của một nhánh gia phả và cây nhánh con,
     * hiển thị tách bạch: Đóng góp trực tiếp của nhánh + Tổng từ các nhánh con (US-11).
     *
     * @param branchId ID của nhánh gia phả
     * @param eventId  Mã sự kiện lọc (tùy chọn)
     * @param maSuKien Alias tiếng Việt cho mã sự kiện (tùy chọn)
     * @param year     Năm đóng góp lọc (tùy chọn)
     */
    @GetMapping({
            "/api/v1/contributions/branches/{branchId}/recursive-summary",
            "/api/v1/branches/{branchId}/contributions/recursive-summary"
    })
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BranchRecursiveContributionResponse>> getRecursiveBranchContributionSummary(
            @PathVariable String branchId,
            @RequestParam(name = "eventId", required = false) String eventId,
            @RequestParam(name = "maSuKien", required = false) String maSuKien,
            @RequestParam(name = "year", required = false) Integer year
    ) {
        String effectiveEventId = (eventId != null && !eventId.isBlank()) ? eventId : maSuKien;
        BranchRecursiveContributionResponse summary = branchContributionService.getRecursiveBranchContributionSummary(
                branchId,
                effectiveEventId,
                year
        );
        return ResponseEntity.ok(ApiResponse.success(
                summary,
                "Branch recursive contribution summary retrieved successfully"
        ));
    }
}
