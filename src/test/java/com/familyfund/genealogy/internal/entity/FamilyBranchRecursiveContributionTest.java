package com.familyfund.genealogy.internal.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit Test kiểm thử trực tiếp giải thuật đệ quy {@code tinhTongDongGopDeQuy(maSuKien)}
 * trên lớp thực thể {@link FamilyBranch} ({@code NhanhGiaDinh}) - US-11.
 */
class FamilyBranchRecursiveContributionTest {

    @Test
    @DisplayName("US-11: tinhTongDongGopDeQuy(maSuKien) cộng dồn chính xác cây 4 thế hệ (Đại Tôn -> Chi -> Phái -> Nhánh)")
    void shouldAggregateContributionsRecursivelyAcrossMultiGenerationTree() {
        String eventId = "EVT-GIO-TO-2026";

        // Thế hệ 4: Nhánh 1.1.1
        FamilyBranch nhanh1 = FamilyBranch.builder()
                .branchId("BR-NHANH-1")
                .clanId("CLAN_DEFAULT")
                .branchName("Nhánh 1 - Phái Nhất")
                .generationLevel(4)
                .childBranches(new ArrayList<>())
                .build();

        // Thế hệ 3: Phái Nhất & Phái Nhì (thuộc Chi 1)
        FamilyBranch phai1 = FamilyBranch.builder()
                .branchId("BR-PHAI-1")
                .clanId("CLAN_DEFAULT")
                .branchName("Phái Nhất - Chi 1")
                .generationLevel(3)
                .childBranches(new ArrayList<>(List.of(nhanh1)))
                .build();
        nhanh1.setParentBranch(phai1);

        FamilyBranch phai2 = FamilyBranch.builder()
                .branchId("BR-PHAI-2")
                .clanId("CLAN_DEFAULT")
                .branchName("Phái Nhì - Chi 1")
                .generationLevel(3)
                .childBranches(new ArrayList<>())
                .build();

        // Thế hệ 2: Chi 1 & Chi 2 (thuộc Đại Tôn)
        FamilyBranch chi1 = FamilyBranch.builder()
                .branchId("BR-CHI-1")
                .clanId("CLAN_DEFAULT")
                .branchName("Chi 1")
                .generationLevel(2)
                .childBranches(new ArrayList<>(List.of(phai1, phai2)))
                .build();
        phai1.setParentBranch(chi1);
        phai2.setParentBranch(chi1);

        FamilyBranch chi2 = FamilyBranch.builder()
                .branchId("BR-CHI-2")
                .clanId("CLAN_DEFAULT")
                .branchName("Chi 2")
                .generationLevel(2)
                .childBranches(new ArrayList<>())
                .build();

        // Thế hệ 1: Đại Tôn (Root)
        FamilyBranch daiTon = FamilyBranch.builder()
                .branchId("BR-ROOT")
                .clanId("CLAN_DEFAULT")
                .branchName("Đại Tôn Nguyễn Tộc")
                .generationLevel(1)
                .childBranches(new ArrayList<>(List.of(chi1, chi2)))
                .build();
        chi1.setParentBranch(daiTon);
        chi2.setParentBranch(daiTon);

        // Bản đồ đóng góp trực tiếp từ CONTRIBUTION_MEMBER_DETAIL cho từng nhánh
        Map<String, BigDecimal> directContributions = new LinkedHashMap<>();
        directContributions.put("BR-ROOT", new BigDecimal("7000000.00"));
        directContributions.put("BR-CHI-1", new BigDecimal("5000000.00"));
        directContributions.put("BR-PHAI-1", new BigDecimal("15300000.00"));
        directContributions.put("BR-NHANH-1", new BigDecimal("2000000.00"));
        directContributions.put("BR-PHAI-2", new BigDecimal("3000000.00"));
        directContributions.put("BR-CHI-2", new BigDecimal("50000000.00"));

        // Kiểm tra hàm đệ quy 2 tham số tinhTongDongGopDeQuy(maSuKien, directBranchContributions)
        BigDecimal totalDaiTon = daiTon.tinhTongDongGopDeQuy(eventId, directContributions);
        assertEquals(0, new BigDecimal("82300000.00").compareTo(totalDaiTon));

        // Kiểm tra hàm đệ quy 1 tham số tinhTongDongGopDeQuy(maSuKien) trên từng nút sau khi hydrate
        assertEquals(0, new BigDecimal("2000000.00").compareTo(nhanh1.tinhTongDongGopDeQuy(eventId)));
        assertEquals(0, new BigDecimal("17300000.00").compareTo(phai1.tinhTongDongGopDeQuy(eventId)));
        assertEquals(0, new BigDecimal("3000000.00").compareTo(phai2.tinhTongDongGopDeQuy(eventId)));
        assertEquals(0, new BigDecimal("25300000.00").compareTo(chi1.tinhTongDongGopDeQuy(eventId)));
        assertEquals(0, new BigDecimal("50000000.00").compareTo(chi2.tinhTongDongGopDeQuy(eventId)));
        assertEquals(0, new BigDecimal("82300000.00").compareTo(daiTon.tinhTongDongGopDeQuy(eventId)));

        // Kiểm tra tách bạch đóng góp từ các nhánh con (descendantsTotal)
        BigDecimal descendantsChi1 = chi1.tinhTongDongGopNhanhConDeQuy(eventId, directContributions);
        assertEquals(0, new BigDecimal("20300000.00").compareTo(descendantsChi1));
    }

    @Test
    @DisplayName("US-11: tinhTongDongGopDeQuy(maSuKien) chống vòng lặp vô hạn và không đếm trùng khi cây có chu trình")
    void shouldPreventInfiniteRecursionAndDoubleCountingOnCyclicGraph() {
        String eventId = "EVT-2026";

        FamilyBranch branchA = FamilyBranch.builder()
                .branchId("BR-A")
                .clanId("CLAN_DEFAULT")
                .branchName("Nhánh A")
                .generationLevel(1)
                .childBranches(new ArrayList<>())
                .build();

        FamilyBranch branchB = FamilyBranch.builder()
                .branchId("BR-B")
                .clanId("CLAN_DEFAULT")
                .branchName("Nhánh B")
                .generationLevel(2)
                .childBranches(new ArrayList<>())
                .build();

        // Tạo chu trình giả lập A -> B -> A
        branchA.getChildBranches().add(branchB);
        branchB.getChildBranches().add(branchA);

        Map<String, BigDecimal> directMap = Map.of(
                "BR-A", new BigDecimal("1000000.00"),
                "BR-B", new BigDecimal("2500000.00")
        );

        BigDecimal total = branchA.tinhTongDongGopDeQuy(eventId, directMap);
        assertEquals(0, new BigDecimal("3500000.00").compareTo(total));
    }

    @Test
    @DisplayName("US-11: tinhTongDongGopDeQuy(maSuKien) phân biệt chính xác nhiều sự kiện khác nhau trên cùng cây nhánh")
    void shouldIsolateContributionsByEventId() {
        FamilyBranch parent = FamilyBranch.builder()
                .branchId("BR-PARENT")
                .clanId("CLAN_DEFAULT")
                .branchName("Chi Trưởng")
                .generationLevel(2)
                .childBranches(new ArrayList<>())
                .build();

        FamilyBranch child = FamilyBranch.builder()
                .branchId("BR-CHILD")
                .clanId("CLAN_DEFAULT")
                .branchName("Phái Nhất")
                .generationLevel(3)
                .childBranches(new ArrayList<>())
                .build();

        parent.getChildBranches().add(child);

        parent.ganDongGopTrucTiep("EVT-SPRING", new BigDecimal("3000000"));
        child.ganDongGopTrucTiep("EVT-SPRING", new BigDecimal("4000000"));

        parent.ganDongGopTrucTiep("EVT-AUTUMN", new BigDecimal("1000000"));
        child.ganDongGopTrucTiep("EVT-AUTUMN", new BigDecimal("500000"));

        assertEquals(0, new BigDecimal("7000000").compareTo(parent.tinhTongDongGopDeQuy("EVT-SPRING")));
        assertEquals(0, new BigDecimal("1500000").compareTo(parent.tinhTongDongGopDeQuy("EVT-AUTUMN")));
    }
}
