package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.response.BranchRecursiveContributionResponse;

import java.math.BigDecimal;

/**
 * Service interface cho nghiệp vụ tính tổng mức đóng góp đệ quy của nhánh gia phả (US-11).
 */
public interface BranchContributionService {

    /**
     * Giải thuật đệ quy {@code tinhTongDongGopDeQuy(branchId, maSuKien)}:
     * Kết hợp PostgreSQL Recursive CTE dưới CSDL và phương thức đệ quy trên thực thể {@code FamilyBranch} ({@code NhanhGiaDinh})
     * để cộng dồn toàn bộ đóng góp từ {@code CONTRIBUTION_MEMBER_DETAIL} của nhánh đang xét và tất cả nhánh con trực thuộc.
     *
     * @param branchId ID của nhánh gia phả đang xét
     * @param maSuKien Mã sự kiện cần lọc (có thể null nếu tính toàn bộ sự kiện)
     * @return Tổng số tiền đóng góp đệ quy (Tiền mặt + Giá trị quy đổi hiện vật không hoàn trả)
     */
    BigDecimal tinhTongDongGopDeQuy(String branchId, String maSuKien);

    /**
     * Giải thuật đệ quy {@code tinhTongDongGopDeQuy(branchId, maSuKien, year)} hỗ trợ lọc theo sự kiện và/hoặc năm.
     *
     * @param branchId ID của nhánh gia phả đang xét
     * @param maSuKien Mã sự kiện cần lọc (tùy chọn)
     * @param year     Năm đóng góp cần lọc (tùy chọn)
     * @return Tổng số tiền đóng góp đệ quy
     */
    BigDecimal tinhTongDongGopDeQuy(String branchId, String maSuKien, Integer year);

    /**
     * Trả về báo cáo phân rã chi tiết đóng góp đệ quy của một nhánh gia phả và toàn bộ cây nhánh con bên dưới,
     * tách bạch rõ: Đóng góp trực tiếp của nhánh + Tổng từ các nhánh con, cũng như Tiền mặt + Hiện vật quy đổi.
     *
     * @param branchId ID của nhánh gia phả đang xét
     * @param maSuKien Mã sự kiện cần lọc (tùy chọn)
     * @param year     Năm đóng góp cần lọc (tùy chọn)
     * @return {@link BranchRecursiveContributionResponse} chứa cây phân rã đệ quy đầy đủ
     */
    BranchRecursiveContributionResponse getRecursiveBranchContributionSummary(
            String branchId,
            String maSuKien,
            Integer year
    );
}
