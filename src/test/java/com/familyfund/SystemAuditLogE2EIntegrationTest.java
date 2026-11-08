package com.familyfund;

import com.familyfund.core.audit.model.AuditAction;
import com.familyfund.core.audit.model.AuditEntityType;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.tenant.TenantFilter;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.internal.dto.request.UpdateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventStatusRequest;
import com.familyfund.event.internal.service.EventService;
import com.familyfund.genealogy.internal.dto.request.UpdateBranchRequest;
import com.familyfund.genealogy.internal.service.GenealogyService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SystemAuditLogE2EIntegrationTest {

    private static final String CLAN_ID = "CLAN_DEFAULT";
    private static final String CLAN_OTHER = "CLAN_OTHER_AUDIT";
    private static final String BRANCH_ID = "BR_AUDIT_E2E_01";
    private static final String MEMBER_ID = "MEM_AUDIT_E2E_01";
    private static final String ACCOUNT_ID = "ACC_AUDIT_E2E_01";
    private static final String EVENT_ID = "EVT_AUDIT_E2E_01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventService eventService;

    @Autowired
    private GenealogyService genealogyService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupTestData() {
        TenantContext.setClanId(CLAN_ID);

        // Clean up previous test state
        entityManager.createNativeQuery("DELETE FROM audit_logs WHERE clan_id IN ('" + CLAN_ID + "', '" + CLAN_OTHER + "')").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM events WHERE event_id = '" + EVENT_ID + "'").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM user_accounts WHERE account_id = '" + ACCOUNT_ID + "'").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM members WHERE member_id = '" + MEMBER_ID + "'").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM family_branches WHERE branch_id = '" + BRANCH_ID + "'").executeUpdate();

        // 1. Clan
        entityManager.createNativeQuery(
                "INSERT INTO clans (clan_id, clan_name) VALUES ('" + CLAN_ID + "', 'Dòng Họ Audit E2E') " +
                        "ON CONFLICT (clan_id) DO NOTHING"
        ).executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO clans (clan_id, clan_name) VALUES ('" + CLAN_OTHER + "', 'Dòng Họ Khác Audit') " +
                        "ON CONFLICT (clan_id) DO NOTHING"
        ).executeUpdate();

        // 2. Branch
        entityManager.createNativeQuery(
                "INSERT INTO family_branches (branch_id, clan_id, branch_name, generation_level, description) " +
                        "VALUES ('" + BRANCH_ID + "', '" + CLAN_ID + "', 'Chi 1 Audit E2E', 2, 'Nhánh 1 test Audit E2E')"
        ).executeUpdate();

        // 3. Member
        entityManager.createNativeQuery(
                "INSERT INTO members (member_id, full_name, gender, generation, branch_id) " +
                        "VALUES ('" + MEMBER_ID + "', 'Nguyễn Văn Audit', 'Male', 2, '" + BRANCH_ID + "')"
        ).executeUpdate();

        // 4. User Account
        entityManager.createNativeQuery(
                "INSERT INTO user_accounts (account_id, member_id, phone_number, email, password_hash, account_status) " +
                        "VALUES ('" + ACCOUNT_ID + "', '" + MEMBER_ID + "', '0999111222', 'audit.e2e@familyfund.vn', 'hash_pw', 'Active')"
        ).executeUpdate();

        // 5. Event
        entityManager.createNativeQuery(
                "INSERT INTO events (event_id, clan_id, event_name, location, event_datetime, status, estimated_budget, created_by) " +
                        "VALUES ('" + EVENT_ID + "', '" + CLAN_ID + "', 'Lễ Giỗ Tổ Audit E2E', 'Từ Đường', '2026-12-01 10:00:00+07', 'Draft', 5000000, '" + ACCOUNT_ID + "')"
        ).executeUpdate();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = {"CLAN_HEAD"})
    @DisplayName("E2E: Event update & status change automatically generate audit logs with old/new values")
    void testEventAuditGenerationAndQuery() throws Exception {
        // 1. Update event details
        UpdateEventRequest updateReq = new UpdateEventRequest(
                "Lễ Giỗ Tổ Audit E2E (Đã đổi tên)",
                Instant.parse("2026-12-02T10:00:00Z"),
                "15/10 AL",
                "Nhà Thờ Mới",
                new BigDecimal("8000000.00"),
                "Mô tả cập nhật"
        );
        eventService.updateEvent(EVENT_ID, updateReq);

        // 2. Change event status to RegistrationOpen
        eventService.updateEventStatus(EVENT_ID, new UpdateEventStatusRequest(EventStatus.RegistrationOpen));

        // 3. Query audit logs via API
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("entityType", "EVENT")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data.items[0].entityType").value("EVENT"))
                .andExpect(jsonPath("$.data.items[0].entityId").value(EVENT_ID))
                .andExpect(jsonPath("$.data.items[0].newValue").isMap())
                .andExpect(jsonPath("$.data.items[0].oldValue").isMap());

        // 4. Specifically query status change
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("actionType", "STATUS_CHANGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].actionType").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$.data.items[0].oldValue.status").value("Draft"))
                .andExpect(jsonPath("$.data.items[0].newValue.status").value("RegistrationOpen"));
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = {"CLAN_HEAD"})
    @DisplayName("E2E: Branch update generates audit log with old and new branch name")
    void testBranchAuditGeneration() throws Exception {
        TenantContext.setClanId(CLAN_ID);

        UpdateBranchRequest branchReq = UpdateBranchRequest.builder()
                .branchName("Chi 1 Audit E2E - Đổi Tên")
                .description("Mô tả mới")
                .build();
        genealogyService.updateBranch(BRANCH_ID, branchReq);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("entityType", "BRANCH")
                        .param("actionType", "UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.items[0].entityType").value("BRANCH"))
                .andExpect(jsonPath("$.data.items[0].entityId").value(BRANCH_ID))
                .andExpect(jsonPath("$.data.items[0].oldValue.branchName").value("Chi 1 Audit E2E"))
                .andExpect(jsonPath("$.data.items[0].newValue.branchName").value("Chi 1 Audit E2E - Đổi Tên"));
    }

    @Test
    @DisplayName("E2E: Database trigger blocks UPDATE on audit_logs table (immutability)")
    void testAuditLogImmutabilityTrigger() {
        String auditId = "AUD_IMMUTABLE_TEST";
        jdbcTemplate.update(
                "INSERT INTO audit_logs (audit_id, clan_id, actor_account_id, action_type, entity_type, description, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                auditId, CLAN_ID, ACCOUNT_ID, "CREATE", "EVENT", "Test log", Timestamp.from(Instant.now())
        );

        // Attempting to UPDATE the audit log row must fail due to trigger audit_logs_block_update
        assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.update("UPDATE audit_logs SET description = 'Tampered' WHERE audit_id = ?", auditId);
        });
    }

    @Test
    @WithMockUser(username = ACCOUNT_ID, roles = {"CLAN_HEAD"})
    @DisplayName("E2E: Multi-tenant isolation prevents accessing audit logs of other clans")
    void testTenantIsolation() throws Exception {
        String auditIdOther = "AUD_OTHER_CLAN_01";
        jdbcTemplate.update(
                "INSERT INTO audit_logs (audit_id, clan_id, actor_account_id, action_type, entity_type, description, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                auditIdOther, CLAN_OTHER, ACCOUNT_ID, "CREATE", "EVENT", "Other clan log", Timestamp.from(Instant.now())
        );

        // Under CLAN_DEFAULT, fetching AUD_OTHER_CLAN_01 must return 404 Not Found
        mockMvc.perform(get("/api/v1/audit-logs/" + auditIdOther)
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isNotFound());
    }
}
