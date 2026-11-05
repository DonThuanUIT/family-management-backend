package com.familyfund;

import com.familyfund.core.tenant.TenantFilter;
import com.familyfund.event.api.dto.AssignableRoleCode;
import com.familyfund.event.api.dto.RoleScopeType;
import com.familyfund.event.internal.dto.request.RoleAssignmentRequest;
import com.familyfund.event.internal.dto.request.RoleRevocationRequest;
import com.familyfund.core.audit.entity.AuditLog;
import com.familyfund.event.internal.repository.AuditLogRepository;
import com.familyfund.identity.internal.repository.AccountRoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live End-to-End QA Integration Test Suite for US-03: Dual-RBAC Role Assignment & Revocation.
 * Tests full lifecycle against Spring Boot Application Context:
 * - Overview API: Returns branches, candidate members, and account active flags.
 * - Negative test: Assigning Branch Head to a member without active account is BLOCKED.
 * - Positive test: Assigning Branch Head updates family_branches, account_roles, audit_logs.
 * - Positive test: Assigning Event Organizer & Treasurer updates event_personnel, audit_logs.
 * - Positive test: Revoking Branch Head clears representative_id, removes role, records audit_logs.
 * - Positive test: Revoking Event role removes event_personnel, records audit_logs.
 * - Audit logs query API: Returns complete chronological history.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleManagementE2EIntegrationTest {

    private static final String CLAN_ID = "CLAN_DEFAULT";
    private static final String BRANCH_ID_1 = "BR_ROLE_E2E_01";
    private static final String BRANCH_ID_2 = "BR_ROLE_E2E_02";
    private static final String EVENT_ID_1 = "EVT_ROLE_E2E_01";

    private static final String MEMBER_ID_ACTIVE = "MEM_ROLE_ACTIVE_01";
    private static final String MEMBER_ID_INACTIVE = "MEM_ROLE_INACT_02";
    private static final String ACCOUNT_ID_ACTIVE = "ACC_ROLE_ACT_01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AccountRoleRepository accountRoleRepository;

    @BeforeEach
    void seedTestData() {
        // 0. Clean up previous test state for isolation
        entityManager.createNativeQuery("DELETE FROM audit_logs WHERE clan_id = '" + CLAN_ID + "'").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM event_personnel WHERE event_id = '" + EVENT_ID_1 + "'").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM account_roles WHERE account_id = '" + ACCOUNT_ID_ACTIVE + "'").executeUpdate();
        entityManager.createNativeQuery("UPDATE family_branches SET representative_id = NULL WHERE clan_id = '" + CLAN_ID + "'").executeUpdate();

        // 1. Clan
        entityManager.createNativeQuery(
                "INSERT INTO clans (clan_id, clan_name) VALUES ('" + CLAN_ID + "', 'Dòng Họ Test QA E2E') " +
                "ON CONFLICT (clan_id) DO UPDATE SET clan_name = 'Dòng Họ Test QA E2E'"
        ).executeUpdate();

        // 2. Branches
        entityManager.createNativeQuery(
                "INSERT INTO family_branches (branch_id, clan_id, branch_name, generation_level, description) " +
                "VALUES ('" + BRANCH_ID_1 + "', '" + CLAN_ID + "', 'Chi 1 Test QA', 10, 'Nhánh 1 test E2E') " +
                "ON CONFLICT (branch_id) DO UPDATE SET branch_name = 'Chi 1 Test QA', clan_id = '" + CLAN_ID + "'"
        ).executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO family_branches (branch_id, clan_id, branch_name, generation_level, description) " +
                "VALUES ('" + BRANCH_ID_2 + "', '" + CLAN_ID + "', 'Chi 2 Test QA', 11, 'Nhánh 2 test E2E') " +
                "ON CONFLICT (branch_id) DO UPDATE SET branch_name = 'Chi 2 Test QA', clan_id = '" + CLAN_ID + "'"
        ).executeUpdate();

        // 3. Members (Bảng members: requires gender, generation, branch_id)
        entityManager.createNativeQuery(
                "INSERT INTO members (member_id, full_name, gender, generation, branch_id) " +
                "VALUES ('" + MEMBER_ID_ACTIVE + "', 'Nguyễn Văn Active', 'Male', 10, '" + BRANCH_ID_1 + "') " +
                "ON CONFLICT (member_id) DO UPDATE SET full_name = 'Nguyễn Văn Active', gender = 'Male', generation = 10, branch_id = '" + BRANCH_ID_1 + "'"
        ).executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO members (member_id, full_name, gender, generation, branch_id) " +
                "VALUES ('" + MEMBER_ID_INACTIVE + "', 'Nguyễn Văn Inactive', 'Male', 11, '" + BRANCH_ID_2 + "') " +
                "ON CONFLICT (member_id) DO UPDATE SET full_name = 'Nguyễn Văn Inactive', gender = 'Male', generation = 11, branch_id = '" + BRANCH_ID_2 + "'"
        ).executeUpdate();

        // 4. User Accounts (Bảng user_accounts: account_status IN ('PendingApproval', 'Active', 'Suspended', 'Deactivated'))
        entityManager.createNativeQuery(
                "INSERT INTO user_accounts (account_id, member_id, phone_number, email, password_hash, account_status) " +
                "VALUES ('" + ACCOUNT_ID_ACTIVE + "', '" + MEMBER_ID_ACTIVE + "', '0999888777', 'active.role.e2e@familyfund.vn', 'hash_pw', 'Active') " +
                "ON CONFLICT (account_id) DO UPDATE SET account_status = 'Active', member_id = '" + MEMBER_ID_ACTIVE + "', phone_number = '0999888777', email = 'active.role.e2e@familyfund.vn'"
        ).executeUpdate();

        // 5. Events (Bảng events: requires clan_id NOT NULL, created_by NOT NULL)
        entityManager.createNativeQuery(
                "INSERT INTO events (event_id, clan_id, event_name, location, event_datetime, status, estimated_budget, created_by) " +
                "VALUES ('" + EVENT_ID_1 + "', '" + CLAN_ID + "', 'Đại Lễ Giỗ Tổ E2E', 'Từ Đường', '2026-04-18 08:30:00+07', 'RegistrationOpen', 50000000, '" + ACCOUNT_ID_ACTIVE + "') " +
                "ON CONFLICT (event_id) DO UPDATE SET event_name = 'Đại Lễ Giỗ Tổ E2E', clan_id = '" + CLAN_ID + "', created_by = '" + ACCOUNT_ID_ACTIVE + "'"
        ).executeUpdate();
    }

    @Test
    @DisplayName("TC-E2E-01: GET /api/v1/roles/overview trả về danh sách chi nhánh và phân loại trạng thái tài khoản thành viên")
    @WithMockUser(username = "ACC_TRUONG_TOC", roles = {"CLAN_HEAD"})
    void testGetRoleManagementOverview() throws Exception {
        mockMvc.perform(get("/api/v1/roles/overview")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.clanId").value(CLAN_ID))
                .andExpect(jsonPath("$.data.branches", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data.members", hasSize(greaterThanOrEqualTo(2))))
                // Thành viên Active
                .andExpect(jsonPath("$.data.members[?(@.memberId == '" + MEMBER_ID_ACTIVE + "')].hasActiveAccount").value(hasItem(true)))
                // Thành viên Inactive
                .andExpect(jsonPath("$.data.members[?(@.memberId == '" + MEMBER_ID_INACTIVE + "')].hasActiveAccount").value(hasItem(false)));
    }

    @Test
    @DisplayName("TC-E2E-02: Bổ nhiệm Trưởng Chi cho thành viên CHƯA có tài khoản active phải bị CHẶN (400 Bad Request)")
    @WithMockUser(username = "ACC_TRUONG_TOC", roles = {"CLAN_HEAD"})
    void testAssignBranchHeadToInactiveMember_MustFail() throws Exception {
        RoleAssignmentRequest request = new RoleAssignmentRequest(
                MEMBER_ID_INACTIVE,
                AssignableRoleCode.ROLE_BRANCH_HEAD,
                RoleScopeType.BRANCH,
                BRANCH_ID_2,
                "Thử bổ nhiệm thành viên chưa có tài khoản"
        );

        mockMvc.perform(post("/api/v1/roles/assign")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("tài khoản")));
    }

    @Test
    @DisplayName("TC-E2E-03: Bổ nhiệm Trưởng Chi thành công: cập nhật representative_id, account_roles và ghi audit_logs")
    @WithMockUser(username = "ACC_TRUONG_TOC", roles = {"CLAN_HEAD"})
    void testAssignBranchHead_SuccessFlow() throws Exception {
        RoleAssignmentRequest request = new RoleAssignmentRequest(
                MEMBER_ID_ACTIVE,
                AssignableRoleCode.ROLE_BRANCH_HEAD,
                RoleScopeType.BRANCH,
                BRANCH_ID_1,
                "Bổ nhiệm Trưởng chi theo nghị quyết Hội Đồng Tộc Biểu"
        );

        mockMvc.perform(post("/api/v1/roles/assign")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleCode").value("ROLE_BRANCH_HEAD"))
                .andExpect(jsonPath("$.data.targetMemberId").value(MEMBER_ID_ACTIVE));

        // 1. Kiểm tra representative_id trên bảng family_branches
        Object repId = entityManager.createNativeQuery(
                "SELECT representative_id FROM family_branches WHERE branch_id = '" + BRANCH_ID_1 + "'"
        ).getSingleResult();
        assertEquals(MEMBER_ID_ACTIVE, repId, "Representative ID trên nhánh phải được cập nhật");

        // 2. Kiểm tra account_roles (cột là role_id)
        Object roleCount = entityManager.createNativeQuery(
                "SELECT count(*) FROM account_roles WHERE account_id = '" + ACCOUNT_ID_ACTIVE + "' AND role_id = 'ROLE_BRANCH_HEAD'"
        ).getSingleResult();
        assertEquals(1L, ((Number) roleCount).longValue(), "Bảng account_roles phải có bản ghi ROLE_BRANCH_HEAD");

        // 3. Kiểm tra audit_logs
        List<AuditLog> logs = auditLogRepository.findByClanIdOrderByCreatedAtDesc(CLAN_ID);
        assertFalse(logs.isEmpty(), "Audit log không được rỗng");
        AuditLog latestLog = logs.get(0);
        assertEquals("ASSIGN", latestLog.getActionType());
        assertEquals("ROLE_BRANCH_HEAD", latestLog.getRoleCode());
        assertEquals(MEMBER_ID_ACTIVE, latestLog.getTargetMemberId());
        assertEquals("Bổ nhiệm Trưởng chi theo nghị quyết Hội Đồng Tộc Biểu", latestLog.getDescription());
    }

    @Test
    @DisplayName("TC-E2E-04: Bổ nhiệm Trưởng Ban Tổ Chức & Thủ Quỹ Sự Kiện cập nhật event_personnel và audit_logs")
    @WithMockUser(username = "ACC_TRUONG_TOC", roles = {"CLAN_HEAD"})
    void testAssignEventRoles_SuccessFlow() throws Exception {
        // Bổ nhiệm Trưởng BTC
        RoleAssignmentRequest headReq = new RoleAssignmentRequest(
                MEMBER_ID_ACTIVE,
                AssignableRoleCode.HeadOfOrganizer,
                RoleScopeType.EVENT,
                EVENT_ID_1,
                "Chỉ định Trưởng Ban Tổ Chức"
        );

        mockMvc.perform(post("/api/v1/roles/assign")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(headReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleCode").value("HeadOfOrganizer"));

        // Bổ nhiệm Thủ Quỹ Sự Kiện
        RoleAssignmentRequest treasurerReq = new RoleAssignmentRequest(
                MEMBER_ID_INACTIVE, // Thành viên BTC/Thủ quỹ không bắt buộc có user_account
                AssignableRoleCode.EventTreasurer,
                RoleScopeType.EVENT,
                EVENT_ID_1,
                "Chỉ định Thủ Quỹ Sự Kiện phụ trách hòm công đức"
        );

        mockMvc.perform(post("/api/v1/roles/assign")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(treasurerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleCode").value("EventTreasurer"));

        // Kiểm tra bảng event_personnel
        Object personnelCount = entityManager.createNativeQuery(
                "SELECT count(*) FROM event_personnel WHERE event_id = '" + EVENT_ID_1 + "'"
        ).getSingleResult();
        assertEquals(2L, ((Number) personnelCount).longValue(), "Bảng event_personnel phải có đúng 2 nhân sự");
    }

    @Test
    @DisplayName("TC-E2E-05: Thu hồi vai trò Trưởng Chi: xóa representative_id, xóa account_roles và ghi nhận lý do vào audit_logs")
    @WithMockUser(username = "ACC_TRUONG_TOC", roles = {"CLAN_HEAD"})
    void testRevokeBranchHead_SuccessFlow() throws Exception {
        // 1. Cấp vai trò trước
        RoleAssignmentRequest assignReq = new RoleAssignmentRequest(
                MEMBER_ID_ACTIVE,
                AssignableRoleCode.ROLE_BRANCH_HEAD,
                RoleScopeType.BRANCH,
                BRANCH_ID_1,
                "Bổ nhiệm ban đầu"
        );
        mockMvc.perform(post("/api/v1/roles/assign")
                .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 2. Thực hiện thu hồi kèm lý do
        RoleRevocationRequest revokeReq = new RoleRevocationRequest(
                MEMBER_ID_ACTIVE,
                AssignableRoleCode.ROLE_BRANCH_HEAD,
                RoleScopeType.BRANCH,
                BRANCH_ID_1,
                "Bàn giao chức trách Trưởng Chi cho thế hệ kế nhiệm"
        );

        mockMvc.perform(post("/api/v1/roles/revoke")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revokeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.actionType").value("REVOKE"));

        // 3. Kiểm tra representative_id trên branch đã được dọn sạch về null
        Object repId = entityManager.createNativeQuery(
                "SELECT representative_id FROM family_branches WHERE branch_id = '" + BRANCH_ID_1 + "'"
        ).getSingleResult();
        assertNull(repId, "Representative ID trên nhánh phải được xóa về NULL");

        // 4. Kiểm tra account_roles đã xóa
        Object roleCount = entityManager.createNativeQuery(
                "SELECT count(*) FROM account_roles WHERE account_id = '" + ACCOUNT_ID_ACTIVE + "' AND role_id = 'ROLE_BRANCH_HEAD'"
        ).getSingleResult();
        assertEquals(0L, ((Number) roleCount).longValue(), "Vai trò trong account_roles phải bị xóa");

        // 5. Kiểm tra audit_logs có bản ghi REVOKE với lý do
        List<AuditLog> logs = auditLogRepository.findByClanIdOrderByCreatedAtDesc(CLAN_ID);
        AuditLog latestRevokeLog = logs.get(0);
        assertEquals("REVOKE", latestRevokeLog.getActionType());
        assertEquals("Bàn giao chức trách Trưởng Chi cho thế hệ kế nhiệm", latestRevokeLog.getDescription());
    }

    @Test
    @DisplayName("TC-E2E-06: Thu hồi vai trò Sự Kiện xóa khỏi event_personnel và ghi audit_logs")
    @WithMockUser(username = "ACC_TRUONG_TOC", roles = {"CLAN_HEAD"})
    void testRevokeEventRole_SuccessFlow() throws Exception {
        // Cấp trước
        RoleAssignmentRequest assignReq = new RoleAssignmentRequest(
                MEMBER_ID_ACTIVE,
                AssignableRoleCode.EventTreasurer,
                RoleScopeType.EVENT,
                EVENT_ID_1,
                "Cấp ban đầu"
        );
        mockMvc.perform(post("/api/v1/roles/assign")
                .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Thu hồi
        RoleRevocationRequest revokeReq = new RoleRevocationRequest(
                MEMBER_ID_ACTIVE,
                AssignableRoleCode.EventTreasurer,
                RoleScopeType.EVENT,
                EVENT_ID_1,
                "Hoàn thành kiểm toán công đức rằm tháng Chạp"
        );

        mockMvc.perform(post("/api/v1/roles/revoke")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revokeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.actionType").value("REVOKE"));

        // Kiểm tra event_personnel không còn người này
        Object personnelCount = entityManager.createNativeQuery(
                "SELECT count(*) FROM event_personnel WHERE event_id = '" + EVENT_ID_1 + "' AND member_id = '" + MEMBER_ID_ACTIVE + "'"
        ).getSingleResult();
        assertEquals(0L, ((Number) personnelCount).longValue(), "Bản ghi event_personnel phải bị xóa");
    }

    @Test
    @DisplayName("TC-E2E-07: GET /api/v1/roles/audit-logs trả về lịch sử đầy đủ theo thời gian thực")
    @WithMockUser(username = "ACC_TRUONG_TOC", roles = {"CLAN_HEAD"})
    void testGetAuditLogs_ReturnsChronologicalLogs() throws Exception {
        // Tạo 1 hành động Cấp và 1 hành động Thu hồi
        RoleAssignmentRequest assignReq = new RoleAssignmentRequest(
                MEMBER_ID_ACTIVE,
                AssignableRoleCode.ROLE_BRANCH_HEAD,
                RoleScopeType.BRANCH,
                BRANCH_ID_1,
                "Bổ nhiệm Trưởng chi"
        );
        mockMvc.perform(post("/api/v1/roles/assign")
                .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        RoleRevocationRequest revokeReq = new RoleRevocationRequest(
                MEMBER_ID_ACTIVE,
                AssignableRoleCode.ROLE_BRANCH_HEAD,
                RoleScopeType.BRANCH,
                BRANCH_ID_1,
                "Thu hồi Trưởng chi"
        );
        mockMvc.perform(post("/api/v1/roles/revoke")
                .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(revokeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Gọi API truy vấn audit log
        mockMvc.perform(get("/api/v1/roles/audit-logs")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[0].actionType").value("REVOKE"))
                .andExpect(jsonPath("$.data[1].actionType").value("ASSIGN"));
    }
}
