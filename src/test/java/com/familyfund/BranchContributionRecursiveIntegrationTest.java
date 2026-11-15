package com.familyfund;

import com.familyfund.contribution.api.ContributionQueryService;
import com.familyfund.contribution.internal.dto.response.BranchRecursiveContributionResponse;
import com.familyfund.contribution.internal.service.BranchContributionService;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.tenant.TenantFilter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test toàn diện cho US-11: Giải thuật đệ quy {@code tinhTongDongGopDeQuy(maSuKien)}
 * kết hợp PostgreSQL Recursive CTE dưới CSDL và phương thức đệ quy trên {@code FamilyBranch} ({@code NhanhGiaDinh}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BranchContributionRecursiveIntegrationTest {

    private static final String CLAN_ID = "CLAN_US11";
    private static final String EVENT_2026 = "EVT_US11_2026";
    private static final String EVENT_2025 = "EVT_US11_2025";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BranchContributionService branchContributionService;

    @Autowired
    private ContributionQueryService contributionQueryService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);

        // 1. Tạo Clan kiểm thử độc lập
        entityManager.createNativeQuery("""
                INSERT INTO clans (clan_id, clan_name)
                VALUES ('CLAN_US11', 'Dòng họ Nguyễn Kiểm Thử US-11')
                ON CONFLICT (clan_id) DO NOTHING
                """).executeUpdate();

        // 2. Tạo cây phả hệ 4 thế hệ (Đại Tôn -> Chi 1, Chi 2 -> Phái 1, Phái 2 -> Nhánh 1)
        entityManager.createNativeQuery("""
                INSERT INTO family_branches (branch_id, clan_id, branch_name, generation_level, parent_branch_id)
                VALUES
                    ('BR_US11_ROOT',   'CLAN_US11', 'Đại Tôn US-11',          1, NULL),
                    ('BR_US11_CHI1',   'CLAN_US11', 'Chi 1 US-11',            2, 'BR_US11_ROOT'),
                    ('BR_US11_CHI2',   'CLAN_US11', 'Chi 2 US-11',            2, 'BR_US11_ROOT'),
                    ('BR_US11_PHAI1',  'CLAN_US11', 'Phái Nhất - Chi 1',      3, 'BR_US11_CHI1'),
                    ('BR_US11_PHAI2',  'CLAN_US11', 'Phái Nhì - Chi 1',       3, 'BR_US11_CHI1'),
                    ('BR_US11_NHANH1', 'CLAN_US11', 'Nhánh 1 - Phái Nhất',    4, 'BR_US11_PHAI1')
                """).executeUpdate();

        // 3. Tạo thành viên trực thuộc từng nhánh
        entityManager.createNativeQuery("""
                INSERT INTO members (member_id, full_name, gender, generation, status, branch_id, is_placeholder)
                VALUES
                    ('MEM_US11_ROOT', 'Nguyễn Trọng Tổ',   'Male',   1, 'Living', 'BR_US11_ROOT',   FALSE),
                    ('MEM_US11_C1',   'Nguyễn Văn Chi 1',  'Male',   2, 'Living', 'BR_US11_CHI1',   FALSE),
                    ('MEM_US11_C2',   'Nguyễn Văn Chi 2',  'Male',   2, 'Living', 'BR_US11_CHI2',   FALSE),
                    ('MEM_US11_P1',   'Nguyễn Văn Phái 1', 'Male',   3, 'Living', 'BR_US11_PHAI1',  FALSE),
                    ('MEM_US11_P2',   'Nguyễn Thị Phái 2', 'Female', 3, 'Living', 'BR_US11_PHAI2',  FALSE),
                    ('MEM_US11_N1',   'Nguyễn Minh Nhánh', 'Male',   4, 'Living', 'BR_US11_NHANH1', FALSE)
                """).executeUpdate();

        // 4. Tạo tài khoản người thu & người tạo sự kiện
        entityManager.createNativeQuery("""
                INSERT INTO user_accounts (account_id, member_id, phone_number, email, password_hash, account_status)
                VALUES ('ACC_US11_HEAD', 'MEM_US11_ROOT', '0999888777', 'us11.head@familyfund.vn', 'hash', 'Active')
                """).executeUpdate();

        // 5. Tạo 2 sự kiện (năm 2026 và năm 2025)
        entityManager.createNativeQuery("""
                INSERT INTO events (event_id, clan_id, event_name, event_datetime, event_date_lunar, location, estimated_budget, status, created_by)
                VALUES
                    ('EVT_US11_2026', 'CLAN_US11', 'Giỗ Tổ Xuân 2026', '2026-03-28 08:00:00+00', '10/03 Bính Ngọ', 'Từ đường', 50000000.00, 'Ongoing', 'ACC_US11_HEAD'),
                    ('EVT_US11_2025', 'CLAN_US11', 'Giỗ Tổ Xuân 2025', '2025-04-07 08:00:00+00', '10/03 Ất Tỵ',    'Từ đường', 40000000.00, 'Closed',  'ACC_US11_HEAD')
                """).executeUpdate();

        // 6. Ghi nhận đóng góp cho sự kiện EVT_US11_2026:
        // 6.1. Phiếu Cash 1: Thành viên Đại Tôn đóng góp trực tiếp 5,000,000
        entityManager.createNativeQuery("""
                INSERT INTO contributions (contribution_id, event_id, payer_member_id, receiver_account_id, contribution_date, contribution_type, total_monetary_value)
                VALUES ('CTB_US11_01', 'EVT_US11_2026', 'MEM_US11_ROOT', 'ACC_US11_HEAD', '2026-03-28 09:00:00+00', 'Cash', 5000000.00);
                INSERT INTO cash_contributions (contribution_id, amount, payment_method, receipt_number)
                VALUES ('CTB_US11_01', 5000000.00, 'Cash', 'PT-US11-001');
                INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
                VALUES ('CTB_US11_01', 'MEM_US11_ROOT', 5000000.00);
                """).executeUpdate();

        // 6.2. Phiếu Cash 2 (Đồng đóng góp / Joint Contribution 9,000,000 chia cho 3 thành viên ở 3 nhánh khác nhau):
        // - MEM_US11_C1 (Chi 1): 4,000,000
        // - MEM_US11_P1 (Phái 1): 3,000,000
        // - MEM_US11_N1 (Nhánh 1): 2,000,000
        entityManager.createNativeQuery("""
                INSERT INTO contributions (contribution_id, event_id, payer_member_id, receiver_account_id, contribution_date, contribution_type, total_monetary_value)
                VALUES ('CTB_US11_02', 'EVT_US11_2026', 'MEM_US11_C1', 'ACC_US11_HEAD', '2026-03-28 10:00:00+00', 'Cash', 9000000.00);
                INSERT INTO cash_contributions (contribution_id, amount, payment_method, receipt_number)
                VALUES ('CTB_US11_02', 9000000.00, 'BankTransfer', 'PT-US11-002');
                INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
                VALUES
                    ('CTB_US11_02', 'MEM_US11_C1', 4000000.00),
                    ('CTB_US11_02', 'MEM_US11_P1', 3000000.00),
                    ('CTB_US11_02', 'MEM_US11_N1', 2000000.00);
                """).executeUpdate();

        // 6.3. Phiếu Hiện vật 1 (Consumed - được tính vào tổng đóng góp): MEM_US11_P2 (Phái 2) = 6,000,000
        entityManager.createNativeQuery("""
                INSERT INTO contributions (contribution_id, event_id, payer_member_id, receiver_account_id, contribution_date, contribution_type, total_monetary_value)
                VALUES ('CTB_US11_03', 'EVT_US11_2026', 'MEM_US11_P2', 'ACC_US11_HEAD', '2026-03-28 11:00:00+00', 'InKind', 6000000.00);
                INSERT INTO in_kind_contributions (contribution_id, item_name, quantity, unit, estimated_value, conversion_method, converted_monetary_value, processing_status, receipt_number)
                VALUES ('CTB_US11_03', 'Mâm lễ xôi gà', 12.0, 'mâm', 6000000.00, 'BtcBenchmark', 6000000.00, 'Consumed', 'PT-US11-INK01');
                INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
                VALUES ('CTB_US11_03', 'MEM_US11_P2', 6000000.00);
                """).executeUpdate();

        // 6.4. Phiếu Hiện vật 2 (AddedToCommonAssets - được tính vào tổng đóng góp): MEM_US11_C2 (Chi 2) = 15,000,000
        entityManager.createNativeQuery("""
                INSERT INTO contributions (contribution_id, event_id, payer_member_id, receiver_account_id, contribution_date, contribution_type, total_monetary_value)
                VALUES ('CTB_US11_04', 'EVT_US11_2026', 'MEM_US11_C2', 'ACC_US11_HEAD', '2026-03-28 11:30:00+00', 'InKind', 15000000.00);
                INSERT INTO in_kind_contributions (contribution_id, item_name, quantity, unit, estimated_value, conversion_method, converted_monetary_value, processing_status, receipt_number)
                VALUES ('CTB_US11_04', 'Lư hương đồng', 1.0, 'bộ', 15000000.00, 'Agreement', 15000000.00, 'AddedToCommonAssets', 'PT-US11-INK02');
                INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
                VALUES ('CTB_US11_04', 'MEM_US11_C2', 15000000.00);
                """).executeUpdate();

        // 6.5. Phiếu Hiện vật 3 (Returned - đồ cho mượn đã hoàn trả gia chủ -> PHẢI LOẠI TRỪ): MEM_US11_N1 (Nhánh 1) = 20,000,000
        entityManager.createNativeQuery("""
                INSERT INTO contributions (contribution_id, event_id, payer_member_id, receiver_account_id, contribution_date, contribution_type, total_monetary_value)
                VALUES ('CTB_US11_05', 'EVT_US11_2026', 'MEM_US11_N1', 'ACC_US11_HEAD', '2026-03-28 12:00:00+00', 'InKind', 20000000.00);
                INSERT INTO in_kind_contributions (contribution_id, item_name, quantity, unit, estimated_value, conversion_method, converted_monetary_value, processing_status, receipt_number)
                VALUES ('CTB_US11_05', 'Nhà bạt sự kiện cho mượn', 1.0, 'bộ', 20000000.00, 'Agreement', 20000000.00, 'Returned', 'PT-US11-INK03');
                INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
                VALUES ('CTB_US11_05', 'MEM_US11_N1', 20000000.00);
                """).executeUpdate();

        // 7. Ghi nhận đóng góp cho sự kiện EVT_US11_2025 (Năm 2025): MEM_US11_N1 (Nhánh 1) = 8,000,000
        entityManager.createNativeQuery("""
                INSERT INTO contributions (contribution_id, event_id, payer_member_id, receiver_account_id, contribution_date, contribution_type, total_monetary_value)
                VALUES ('CTB_US11_06', 'EVT_US11_2025', 'MEM_US11_N1', 'ACC_US11_HEAD', '2025-04-07 09:00:00+00', 'Cash', 8000000.00);
                INSERT INTO cash_contributions (contribution_id, amount, payment_method, receipt_number)
                VALUES ('CTB_US11_06', 8000000.00, 'Cash', 'PT-US11-2025-01');
                INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
                VALUES ('CTB_US11_06', 'MEM_US11_N1', 8000000.00);
                """).executeUpdate();

        entityManager.flush();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("US-11: tinhTongDongGopDeQuy(maSuKien) tính đúng từng cấp nhánh từ lá lên Đại Tôn và loại trừ hiện vật Returned")
    void shouldCalculateExactRecursiveTotalsAtEveryBranchLevelAndExcludeReturnedInKind() {
        // Cấp 4 (Lá): Nhánh 1 chỉ tính 2,000,000 từ phiếu đồng đóng góp, loại trừ 20,000,000 hiện vật Returned
        BigDecimal totalNhanh1 = branchContributionService.tinhTongDongGopDeQuy("BR_US11_NHANH1", EVENT_2026);
        assertEquals(0, new BigDecimal("2000000.00").compareTo(totalNhanh1));

        // Cấp 3: Phái Nhất = 3,000,000 (trực tiếp) + 2,000,000 (Nhánh 1) = 5,000,000
        BigDecimal totalPhai1 = branchContributionService.tinhTongDongGopDeQuy("BR_US11_PHAI1", EVENT_2026);
        assertEquals(0, new BigDecimal("5000000.00").compareTo(totalPhai1));

        // Cấp 3: Phái Nhì = 6,000,000 (hiện vật Consumed)
        BigDecimal totalPhai2 = branchContributionService.tinhTongDongGopDeQuy("BR_US11_PHAI2", EVENT_2026);
        assertEquals(0, new BigDecimal("6000000.00").compareTo(totalPhai2));

        // Cấp 2: Chi 1 = 4,000,000 (trực tiếp) + 5,000,000 (Phái 1) + 6,000,000 (Phái 2) = 15,000,000
        BigDecimal totalChi1 = branchContributionService.tinhTongDongGopDeQuy("BR_US11_CHI1", EVENT_2026);
        assertEquals(0, new BigDecimal("15000000.00").compareTo(totalChi1));

        // Cấp 2: Chi 2 = 15,000,000 (hiện vật AddedToCommonAssets)
        BigDecimal totalChi2 = branchContributionService.tinhTongDongGopDeQuy("BR_US11_CHI2", EVENT_2026);
        assertEquals(0, new BigDecimal("15000000.00").compareTo(totalChi2));

        // Cấp 1 (Gốc): Đại Tôn = 5,000,000 (trực tiếp) + 15,000,000 (Chi 1) + 15,000,000 (Chi 2) = 35,000,000
        BigDecimal totalRoot = contributionQueryService.tinhTongDongGopDeQuy("BR_US11_ROOT", EVENT_2026);
        assertEquals(0, new BigDecimal("35000000.00").compareTo(totalRoot));
    }

    @Test
    @DisplayName("US-11: Hỗ trợ lọc theo năm (year) và tính tổng toàn thời gian khi không truyền maSuKien")
    void shouldSupportFilteringByYearAndAllTimeTotal() {
        // Lọc riêng năm 2025 trên Đại Tôn -> chỉ có 8,000,000 từ Nhánh 1
        BigDecimal total2025 = branchContributionService.tinhTongDongGopDeQuy("BR_US11_ROOT", null, 2025);
        assertEquals(0, new BigDecimal("8000000.00").compareTo(total2025));

        // Không truyền maSuKien và không truyền year -> Cộng dồn cả 2025 (8M) + 2026 (35M) = 43,000,000
        BigDecimal totalAllTime = branchContributionService.tinhTongDongGopDeQuy("BR_US11_ROOT", null, null);
        assertEquals(0, new BigDecimal("43000000.00").compareTo(totalAllTime));

        // Kiểm tra cấu trúc bóc tách của getRecursiveBranchContributionSummary cho Chi 1 ở sự kiện 2026
        BranchRecursiveContributionResponse chi1Summary = branchContributionService.getRecursiveBranchContributionSummary(
                "BR_US11_CHI1", EVENT_2026, null
        );
        assertNotNull(chi1Summary);
        assertEquals(0, new BigDecimal("4000000.00").compareTo(chi1Summary.directTotalAmount()));
        assertEquals(0, new BigDecimal("11000000.00").compareTo(chi1Summary.descendantsTotalAmount()));
        assertEquals(0, new BigDecimal("15000000.00").compareTo(chi1Summary.totalRecursiveAmount()));
        assertEquals(0, new BigDecimal("9000000.00").compareTo(chi1Summary.totalRecursiveCashAmount()));
        assertEquals(0, new BigDecimal("6000000.00").compareTo(chi1Summary.totalRecursiveInKindAmount()));
    }

    @Test
    @WithMockUser(username = "0999888777", roles = {"MEMBER"})
    @DisplayName("US-11 REST API: GET /api/v1/contributions/branches/{branchId}/recursive-summary & recursive-total")
    void shouldExposeRecursiveSummaryAndTotalViaRestEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/contributions/branches/BR_US11_ROOT/recursive-total")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("maSuKien", EVENT_2026))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", is(35000000.00)));

        mockMvc.perform(get("/api/v1/contributions/branches/BR_US11_ROOT/recursive-summary")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("eventId", EVENT_2026))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.branchId", is("BR_US11_ROOT")))
                .andExpect(jsonPath("$.data.eventId", is(EVENT_2026)))
                .andExpect(jsonPath("$.data.directTotalAmount", is(5000000.00)))
                .andExpect(jsonPath("$.data.descendantsTotalAmount", is(30000000.00)))
                .andExpect(jsonPath("$.data.totalRecursiveAmount", is(35000000.00)))
                .andExpect(jsonPath("$.data.totalRecursiveCashAmount", is(14000000.00)))
                .andExpect(jsonPath("$.data.totalRecursiveInKindAmount", is(21000000.00)))
                .andExpect(jsonPath("$.data.childBranches", hasSize(2)));
    }
}
