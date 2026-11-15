package com.familyfund;

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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test toàn diện trên PostgreSQL cho US-37:
 * Tổng hợp và so sánh {@code estimated_budget} của sự kiện với tổng {@code advance_budget} (công việc),
 * tổng {@code advance_amount} (tạm ứng) và {@code actual_expense_amount} (thực chi).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventBudgetSummaryIntegrationTest {

    private static final String CLAN_ID = "CLAN_US37";
    private static final String EVENT_OVERRUN = "EVT_US37_OVERRUN";
    private static final String EVENT_SAFE = "EVT_US37_SAFE";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);

        entityManager.createNativeQuery("""
                INSERT INTO clans (clan_id, clan_name)
                VALUES ('CLAN_US37', 'Dòng họ Nguyễn Kiểm Thử US-37')
                ON CONFLICT (clan_id) DO NOTHING
                """).executeUpdate();

        entityManager.createNativeQuery("""
                INSERT INTO family_branches (branch_id, clan_id, branch_name, generation_level, parent_branch_id)
                VALUES ('BR_US37_ROOT', 'CLAN_US37', 'Đại Tôn US-37', 1, NULL)
                """).executeUpdate();

        entityManager.createNativeQuery("""
                INSERT INTO members (member_id, full_name, gender, generation, status, branch_id, is_placeholder)
                VALUES ('MEM_US37_01', 'Nguyễn Văn Quản Sự', 'Male', 2, 'Living', 'BR_US37_ROOT', FALSE)
                """).executeUpdate();

        entityManager.createNativeQuery("""
                INSERT INTO user_accounts (account_id, member_id, phone_number, email, password_hash, account_status)
                VALUES ('ACC_US37_01', 'MEM_US37_01', '0977666555', 'us37@familyfund.vn', 'hash', 'Active')
                """).executeUpdate();

        // 1. Tạo 2 sự kiện:
        // - EVT_US37_OVERRUN: estimated_budget = 30,000,000
        // - EVT_US37_SAFE:    estimated_budget = 50,000,000
        entityManager.createNativeQuery("""
                INSERT INTO events (event_id, clan_id, event_name, event_datetime, event_date_lunar, location, estimated_budget, status, created_by)
                VALUES
                    ('EVT_US37_OVERRUN', 'CLAN_US37', 'Giỗ Tổ Vượt Ngân Sách', '2026-04-10 08:00:00+00', '10/03 Bính Ngọ', 'Từ đường', 30000000.00, 'Ongoing', 'ACC_US37_01'),
                    ('EVT_US37_SAFE',    'CLAN_US37', 'Giỗ Tổ Trong Dự Toán',  '2026-05-10 08:00:00+00', '10/04 Bính Ngọ', 'Từ đường', 50000000.00, 'Ongoing', 'ACC_US37_01')
                """).executeUpdate();

        // 2. Tạo 2 hạng mục công việc cho EVT_US37_OVERRUN:
        // - TSK_US37_1: advance_budget = 15,000,000
        // - TSK_US37_2: advance_budget = 12,000,000
        // -> Tổng advance_budget = 27,000,000 (không bị nhân bản dù TSK_US37_1 có 2 phiếu tạm ứng!)
        entityManager.createNativeQuery("""
                INSERT INTO event_tasks (task_id, event_id, task_name, details, advance_budget, deadline, status, assigned_member_id, created_by)
                VALUES
                    ('TSK_US37_1', 'EVT_US37_OVERRUN', 'Đặt cỗ mặn & chay', 'Đặt 30 mâm cỗ', 15000000.00, '2026-04-08 17:00:00+00', 'InProgress', 'MEM_US37_01', 'ACC_US37_01'),
                    ('TSK_US37_2', 'EVT_US37_OVERRUN', 'Thuê nhà bạt & âm thanh', 'Sân từ đường', 12000000.00, '2026-04-09 17:00:00+00', 'Completed',  'MEM_US37_01', 'ACC_US37_01')
                """).executeUpdate();

        // 3. Tạo 3 phiếu tạm ứng (2 phiếu gắn vào TSK_US37_1, 1 phiếu gắn vào TSK_US37_2):
        // Tổng advance_amount = 10,000,000 + 8,000,000 + 10,000,000 = 28,000,000 (<= 30,000,000)
        entityManager.createNativeQuery("""
                INSERT INTO advance_vouchers (advance_voucher_id, task_id, recipient_member_id, advance_amount, received_date, settlement_deadline, commitment_notes, created_by_account_id)
                VALUES
                    ('ADV_US37_1', 'TSK_US37_1', 'MEM_US37_01', 10000000.00, '2026-04-01 09:00:00+00', '2026-04-15 17:00:00+00', 'Tạm ứng đợt 1 cỗ bàn', 'ACC_US37_01'),
                    ('ADV_US37_2', 'TSK_US37_1', 'MEM_US37_01',  8000000.00, '2026-04-05 09:00:00+00', '2026-04-15 17:00:00+00', 'Tạm ứng đợt 2 cỗ bàn', 'ACC_US37_01'),
                    ('ADV_US37_3', 'TSK_US37_2', 'MEM_US37_01', 10000000.00, '2026-04-02 09:00:00+00', '2026-04-15 17:00:00+00', 'Tạm ứng thuê rạp',     'ACC_US37_01')
                """).executeUpdate();

        // 4. Tạo 3 phiếu quyết toán thực chi:
        // - SET_US37_1 (Valid): 12,000,000
        // - SET_US37_2 (NeedsExplanation): 9,500,000
        // - SET_US37_3 (Settled): 11,500,000
        // -> Tổng actual_expense_amount = 33,000,000 (> estimated_budget 30,000,000 -> vượt 3,000,000!)
        // -> Tổng verifiedActualExpenseAmount (Valid + Settled) = 23,500,000
        entityManager.createNativeQuery("""
                INSERT INTO expense_settlement_vouchers (settlement_voucher_id, advance_voucher_id, actual_expense_amount, difference_amount, status, treasurer_account_id)
                VALUES
                    ('SET_US37_1', 'ADV_US37_1', 12000000.00, 2000000.00, 'Valid',            'ACC_US37_01'),
                    ('SET_US37_2', 'ADV_US37_2',  9500000.00, 1500000.00, 'NeedsExplanation', 'ACC_US37_01'),
                    ('SET_US37_3', 'ADV_US37_3', 11500000.00, 1500000.00, 'Settled',          'ACC_US37_01')
                """).executeUpdate();

        entityManager.flush();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(username = "0977666555", roles = {"MEMBER"})
    @DisplayName("US-37 E2E: GET /api/v1/events/{eventId}/budget-summary tính chính xác không nhân bản advance_budget và bật cờ cảnh báo vượt ngân sách")
    void shouldCalculateExactBudgetTotalsWithoutFanoutAndReturnOverrunWarning() throws Exception {
        mockMvc.perform(get("/api/v1/events/" + EVENT_OVERRUN + "/budget-summary")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.eventId", is(EVENT_OVERRUN)))
                .andExpect(jsonPath("$.data.estimatedBudget", is(30000000.00)))
                .andExpect(jsonPath("$.data.totalAdvanceBudget", is(27000000.00)))
                .andExpect(jsonPath("$.data.totalAdvanceAmount", is(28000000.00)))
                .andExpect(jsonPath("$.data.totalActualExpenseAmount", is(33000000.00)))
                .andExpect(jsonPath("$.data.verifiedActualExpenseAmount", is(23500000.00)))
                .andExpect(jsonPath("$.data.unallocatedBudget", is(3000000.00)))
                .andExpect(jsonPath("$.data.remainingBudgetByAdvance", is(2000000.00)))
                .andExpect(jsonPath("$.data.remainingBudgetByActualExpense", is(-3000000.00)))
                .andExpect(jsonPath("$.data.overrunAmount", is(3000000.00)))
                .andExpect(jsonPath("$.data.isOverBudget", is(true)))
                .andExpect(jsonPath("$.data.isAdvanceOverEstimated", is(false)))
                .andExpect(jsonPath("$.data.isActualExpenseOverEstimated", is(true)))
                .andExpect(jsonPath("$.data.isTaskBudgetOverEstimated", is(false)))
                .andExpect(jsonPath("$.data.hasTaskOverBudget", is(true)))
                .andExpect(jsonPath("$.data.warningLevel", is("OVERRUN_WARNING")))
                .andExpect(jsonPath("$.data.totalTaskCount", is(2)))
                .andExpect(jsonPath("$.data.totalAdvanceVoucherCount", is(3)))
                .andExpect(jsonPath("$.data.totalSettlementVoucherCount", is(3)))
                .andExpect(jsonPath("$.data.needsExplanationVoucherCount", is(1)))
                .andExpect(jsonPath("$.data.taskBreakdowns", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "0977666555", roles = {"MEMBER"})
    @DisplayName("US-37 E2E: Sự kiện chưa có phát sinh tạm ứng/thực chi trả về isOverBudget = false")
    void shouldReturnSafeSummaryWhenNoExpensesRecorded() throws Exception {
        mockMvc.perform(get("/api/v1/events/" + EVENT_SAFE + "/budget")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.eventId", is(EVENT_SAFE)))
                .andExpect(jsonPath("$.data.estimatedBudget", is(50000000.00)))
                .andExpect(jsonPath("$.data.totalAdvanceBudget", is(0)))
                .andExpect(jsonPath("$.data.totalAdvanceAmount", is(0)))
                .andExpect(jsonPath("$.data.totalActualExpenseAmount", is(0)))
                .andExpect(jsonPath("$.data.isOverBudget", is(false)))
                .andExpect(jsonPath("$.data.warningLevel", is("NORMAL")));
    }
}
