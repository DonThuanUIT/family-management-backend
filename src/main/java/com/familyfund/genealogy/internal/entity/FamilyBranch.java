package com.familyfund.genealogy.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entity mapping table `family_branches` (NhanhGiaDinh).
 * Represents a hierarchical branch in the family genealogy tree.
 */
@Entity
@Table(name = "family_branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"parentBranch", "childBranches", "members", "representative", "dongGopTrucTiepTheoSuKien"})
public class FamilyBranch {

    public static final String ALL_EVENTS_KEY = "__ALL_EVENTS__";

    @Id
    @Column(name = "branch_id", length = 20, nullable = false)
    private String branchId;

    @Column(name = "clan_id", length = 20, nullable = false)
    private String clanId;

    @Column(name = "branch_name", length = 100, nullable = false)
    private String branchName;

    @Column(name = "generation_level", nullable = false)
    private Integer generationLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_branch_id")
    private FamilyBranch parentBranch;

    @OneToMany(mappedBy = "parentBranch")
    @Builder.Default
    private List<FamilyBranch> childBranches = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "representative_id")
    private Member representative;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "branch")
    @Builder.Default
    private List<Member> members = new ArrayList<>();

    /**
     * Transient cache storing direct branch contribution amounts keyed by eventId (maSuKien)
     * hydrated from CONTRIBUTION_MEMBER_DETAIL at the service layer.
     */
    @Transient
    @Builder.Default
    private Map<String, BigDecimal> dongGopTrucTiepTheoSuKien = new HashMap<>();

    /**
     * Transient direct contribution amount of members belonging directly to this branch.
     */
    @Transient
    @Builder.Default
    private BigDecimal dongGopTrucTiep = BigDecimal.ZERO;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    /**
     * Gán mức đóng góp trực tiếp (tổng hợp từ CONTRIBUTION_MEMBER_DETAIL của các thành viên thuộc nhánh này)
     * tương ứng với mã sự kiện {@code maSuKien}.
     *
     * @param maSuKien Mã sự kiện (hoặc null/rỗng nếu tính tổng toàn thời gian)
     * @param soTien   Tổng mức đóng góp trực tiếp của nhánh
     */
    public void ganDongGopTrucTiep(String maSuKien, BigDecimal soTien) {
        BigDecimal normalizedAmount = (soTien != null) ? soTien : BigDecimal.ZERO;
        this.dongGopTrucTiep = normalizedAmount;
        if (this.dongGopTrucTiepTheoSuKien == null) {
            this.dongGopTrucTiepTheoSuKien = new HashMap<>();
        }
        this.dongGopTrucTiepTheoSuKien.put(normalizeEventKey(maSuKien), normalizedAmount);
    }

    /**
     * Lấy mức đóng góp trực tiếp của các thành viên thuộc chính nhánh đang xét theo {@code maSuKien}.
     *
     * @param maSuKien Mã sự kiện (hoặc null/rỗng nếu tính tổng toàn thời gian)
     * @return Số tiền đóng góp trực tiếp của nhánh (>= 0)
     */
    public BigDecimal layDongGopTrucTiep(String maSuKien) {
        String key = normalizeEventKey(maSuKien);
        if (this.dongGopTrucTiepTheoSuKien != null && this.dongGopTrucTiepTheoSuKien.containsKey(key)) {
            BigDecimal val = this.dongGopTrucTiepTheoSuKien.get(key);
            return val != null ? val : BigDecimal.ZERO;
        }
        return this.dongGopTrucTiep != null ? this.dongGopTrucTiep : BigDecimal.ZERO;
    }

    /**
     * Giải thuật đệ quy {@code tinhTongDongGopDeQuy(maSuKien)} trên lớp {@code FamilyBranch} ({@code NhanhGiaDinh}).
     * Cộng dồn toàn bộ đóng góp trực tiếp từ {@code CONTRIBUTION_MEMBER_DETAIL} của nhánh đang xét
     * và đệ quy xuống tất cả các nhánh con bên dưới.
     *
     * @param maSuKien Mã sự kiện cần tính tổng đóng góp (hoặc null nếu tính trên toàn bộ sự kiện)
     * @return Tổng mức đóng góp đệ quy của nhánh hiện tại và toàn bộ cây nhánh con bên dưới
     */
    public BigDecimal tinhTongDongGopDeQuy(String maSuKien) {
        return tinhTongDongGopDeQuyInternal(maSuKien, null, new HashSet<>());
    }

    /**
     * Giải thuật đệ quy {@code tinhTongDongGopDeQuy(maSuKien, directBranchContributions)} kết hợp bảng băm
     * mức đóng góp trực tiếp của từng nhánh được truy vấn bằng Recursive CTE dưới CSDL.
     *
     * @param maSuKien                  Mã sự kiện cần tính tổng đóng góp (hoặc null nếu tính toàn bộ)
     * @param directBranchContributions Map ánh xạ {@code branchId -> directAmount} từ {@code CONTRIBUTION_MEMBER_DETAIL}
     * @return Tổng mức đóng góp đệ quy của nhánh hiện tại và toàn bộ cây nhánh con bên dưới
     */
    public BigDecimal tinhTongDongGopDeQuy(String maSuKien, Map<String, BigDecimal> directBranchContributions) {
        hydrateDirectContributions(maSuKien, directBranchContributions, new HashSet<>());
        return tinhTongDongGopDeQuyInternal(maSuKien, directBranchContributions, new HashSet<>());
    }

    /**
     * Tính tổng đóng góp đệ quy của riêng các nhánh con bên dưới (không bao gồm đóng góp trực tiếp của nhánh hiện tại).
     *
     * @param maSuKien                  Mã sự kiện cần tính
     * @param directBranchContributions Map ánh xạ {@code branchId -> directAmount}
     * @return Tổng đóng góp từ tất cả các nhánh con bên dưới
     */
    public BigDecimal tinhTongDongGopNhanhConDeQuy(String maSuKien, Map<String, BigDecimal> directBranchContributions) {
        hydrateDirectContributions(maSuKien, directBranchContributions, new HashSet<>());
        Set<String> visited = new HashSet<>();
        if (this.branchId != null) {
            visited.add(this.branchId);
        }
        BigDecimal childrenTotal = BigDecimal.ZERO;
        if (this.childBranches != null && !this.childBranches.isEmpty()) {
            for (FamilyBranch child : this.childBranches) {
                if (child != null) {
                    childrenTotal = childrenTotal.add(
                            child.tinhTongDongGopDeQuyInternal(maSuKien, directBranchContributions, visited)
                    );
                }
            }
        }
        return childrenTotal;
    }

    private void hydrateDirectContributions(
            String maSuKien,
            Map<String, BigDecimal> directBranchContributions,
            Set<String> visited
    ) {
        if (this.branchId != null && !visited.add(this.branchId)) {
            return;
        }
        if (directBranchContributions != null && this.branchId != null) {
            BigDecimal direct = directBranchContributions.getOrDefault(this.branchId, BigDecimal.ZERO);
            ganDongGopTrucTiep(maSuKien, direct);
        }
        if (this.childBranches != null) {
            for (FamilyBranch child : this.childBranches) {
                if (child != null) {
                    child.hydrateDirectContributions(maSuKien, directBranchContributions, visited);
                }
            }
        }
    }

    private BigDecimal tinhTongDongGopDeQuyInternal(
            String maSuKien,
            Map<String, BigDecimal> directBranchContributions,
            Set<String> visitedBranchIds
    ) {
        if (this.branchId != null && !visitedBranchIds.add(this.branchId)) {
            // Bảo vệ chống chu trình đệ quy vô hạn (Cycle guard)
            return BigDecimal.ZERO;
        }

        BigDecimal directAmount;
        if (directBranchContributions != null && this.branchId != null) {
            directAmount = directBranchContributions.getOrDefault(this.branchId, BigDecimal.ZERO);
            if (directAmount == null) {
                directAmount = BigDecimal.ZERO;
            }
        } else {
            directAmount = layDongGopTrucTiep(maSuKien);
        }

        BigDecimal childrenTotal = BigDecimal.ZERO;
        if (this.childBranches != null && !this.childBranches.isEmpty()) {
            for (FamilyBranch child : this.childBranches) {
                if (child != null) {
                    childrenTotal = childrenTotal.add(
                            child.tinhTongDongGopDeQuyInternal(maSuKien, directBranchContributions, visitedBranchIds)
                    );
                }
            }
        }

        return directAmount.add(childrenTotal);
    }

    private static String normalizeEventKey(String maSuKien) {
        return (maSuKien == null || maSuKien.isBlank()) ? ALL_EVENTS_KEY : maSuKien.trim();
    }
}
