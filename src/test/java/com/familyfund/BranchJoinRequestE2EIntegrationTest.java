package com.familyfund;

import com.familyfund.core.tenant.TenantFilter;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.BranchJoinRequest;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import com.familyfund.identity.internal.entity.ProposalType;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.AccountRoleRepository;
import com.familyfund.identity.internal.repository.BranchJoinRequestRepository;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End QA Integration Test Suite for US-17: Đăng ký tự do qua Mã mời chung.
 * Tests the entire lifecycle:
 * Clan Head creates invite code -> Guest verifies code -> Guest views sanitized tree ->
 * Guest submits join request -> Approver views request -> Approver approves -> Member appears on tree.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BranchJoinRequestE2EIntegrationTest {

    private static final String CLAN_ID = "CLAN_DEFAULT";
    private static final String ROOT_BRANCH_ID = "BR_ROOT_E2E";
    private static final String CLAN_HEAD_ACCOUNT_ID = "ACC_CLAN_HEAD_E2E";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private BranchJoinRequestRepository branchJoinRequestRepository;

    @Autowired
    private InvitationCodeRepository invitationCodeRepository;

    @Autowired
    private AccountRoleRepository accountRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpMasterData() {
        // 1. Seed root branch for CLAN_DEFAULT
        entityManager.createNativeQuery(
                "INSERT INTO family_branches (branch_id, clan_id, branch_name, generation_level, description) " +
                "VALUES ('" + ROOT_BRANCH_ID + "', '" + CLAN_ID + "', 'Chi Trưởng Gốc E2E', 1, 'Nhánh gốc kiểm thử E2E') " +
                "ON CONFLICT (branch_id) DO UPDATE SET branch_name = 'Chi Trưởng Gốc E2E'"
        ).executeUpdate();

        // 2. Seed Clan Head UserAccount
        entityManager.createNativeQuery(
                "INSERT INTO user_accounts (account_id, phone_number, email, password_hash, account_status) " +
                "VALUES ('" + CLAN_HEAD_ACCOUNT_ID + "', '0901234567', 'head.e2e@familyfund.vn', '" +
                passwordEncoder.encode("Secret123@") + "', 'Active') " +
                "ON CONFLICT (account_id) DO NOTHING"
        ).executeUpdate();

        // 3. Seed ROLE_CLAN_HEAD in account_roles
        entityManager.createNativeQuery(
                "INSERT INTO account_roles (account_id, role_id) " +
                "VALUES ('" + CLAN_HEAD_ACCOUNT_ID + "', 'ROLE_CLAN_HEAD') " +
                "ON CONFLICT (account_id, role_id) DO NOTHING"
        ).executeUpdate();
    }

    @Test
    @DisplayName("E2E Scenario 1: Prospective member joins ExistingBranchLeaf, gets approved, and appears on tree")
    @WithMockUser(username = CLAN_HEAD_ACCOUNT_ID, roles = {"CLAN_HEAD"})
    void e2e_JoinExistingBranchLeaf_FullFlow_Success() throws Exception {
        // ====================================================================
        // STEP 1: Clan Head generates a general clan invitation code (US-16)
        // ====================================================================
        String createInvitePayload = """
                {
                    "targetBranchId": "BR_ROOT_E2E",
                    "maxUses": 10,
                    "validityDays": 30
                }
                """;

        MvcResult createInviteResult = mockMvc.perform(post("/api/v1/invitations/general")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createInvitePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inviteCode").isNotEmpty())
                .andReturn();

        JsonNode inviteNode = objectMapper.readTree(createInviteResult.getResponse().getContentAsString());
        String inviteCode = inviteNode.at("/data/inviteCode").asText();
        assertNotNull(inviteCode);

        // ====================================================================
        // STEP 2: Prospective member accesses landing page to verify invite code (US-17 Task 1)
        // Public access - unauthenticated
        // ====================================================================
        mockMvc.perform(get("/api/v1/invitations/" + inviteCode)
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
                .andExpect(jsonPath("$.data.maxUses").value(10))
                .andExpect(jsonPath("$.data.usedCount").value(0))
                .andExpect(jsonPath("$.data.clanName").value("Dòng Họ Mẫu Mặc Định"));

        // ====================================================================
        // STEP 3: Prospective member retrieves sanitized onboarding tree (US-17 Task 4)
        // Public access - zero private personal information exposed
        // ====================================================================
        mockMvc.perform(get("/api/v1/invitations/" + inviteCode + "/tree")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.branchId").value(ROOT_BRANCH_ID))
                .andExpect(jsonPath("$.data.branchName").value("Chi Trưởng Gốc E2E"))
                .andExpect(jsonPath("$.data.generationLevel").value(1))
                .andExpect(jsonPath("$.data.memberCount").isNumber());

        // ====================================================================
        // STEP 4: Prospective member submits join request for ExistingBranchLeaf (US-17 Task 2)
        // Public access - unauthenticated
        // ====================================================================
        String joinPayload = """
                {
                    "phoneNumber": "0912888777",
                    "password": "Password123@",
                    "email": "applicant.leaf@familyfund.vn",
                    "fullName": "Nguyễn Văn Hậu Duệ",
                    "dateOfBirth": "1997-04-12",
                    "gender": "Male",
                    "proposalType": "ExistingBranchLeaf",
                    "targetBranchId": "BR_ROOT_E2E",
                    "proposedGeneration": 3,
                    "relationshipNote": "Cháu đời thứ 3 của cụ chi trưởng"
                }
                """;

        MvcResult joinResult = mockMvc.perform(post("/api/v1/invitations/" + inviteCode + "/join")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.approvalStatus").value("Pending"))
                .andExpect(jsonPath("$.data.targetBranchName").value("Chi Trưởng Gốc E2E"))
                .andReturn();

        JsonNode joinResponseNode = objectMapper.readTree(joinResult.getResponse().getContentAsString());
        String requestId = joinResponseNode.at("/data/requestId").asText();
        String accountId = joinResponseNode.at("/data/accountId").asText();

        // Verify PendingApproval state in database
        Optional<UserAccount> pendingAccount = userAccountRepository.findById(accountId);
        assertTrue(pendingAccount.isPresent());
        assertEquals(AccountStatus.PendingApproval, pendingAccount.get().getAccountStatus());
        assertNull(pendingAccount.get().getMemberId());
        assertTrue(passwordEncoder.matches("Password123@", pendingAccount.get().getPasswordHash()));

        // ====================================================================
        // STEP 5: Clan Head retrieves pending join requests list (US-17 Task 5)
        // ====================================================================
        mockMvc.perform(get("/api/v1/join-requests")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("status", "Pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[?(@.requestId == '" + requestId + "')]").exists())
                .andExpect(jsonPath("$.data.content[?(@.phoneNumber == '0912888777')]").exists());

        // ====================================================================
        // STEP 6: Clan Head approves the join request (US-17 Task 3)
        // ====================================================================
        mockMvc.perform(post("/api/v1/join-requests/" + requestId + "/approve")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.approvalStatus").value("Approved"))
                .andExpect(jsonPath("$.data.reviewedByAccountId").value(CLAN_HEAD_ACCOUNT_ID));

        // ====================================================================
        // STEP 7: Verify final database state & Member appears on tree
        // ====================================================================
        // 1. UserAccount is activated and memberId is linked
        UserAccount activeAccount = userAccountRepository.findById(accountId).orElseThrow();
        assertEquals(AccountStatus.Active, activeAccount.getAccountStatus());
        assertNotNull(activeAccount.getMemberId());

        // 2. ROLE_MEMBER is assigned
        assertTrue(accountRoleRepository.existsByIdAccountIdAndIdRoleId(accountId, "ROLE_MEMBER"));

        // 3. InvitationCode used_count is incremented to 1
        var invitation = invitationCodeRepository.findById(inviteCode).orElseThrow();
        assertEquals(1, invitation.getUsedCount());

        // 4. BranchJoinRequest status is Approved
        BranchJoinRequest approvedReq = branchJoinRequestRepository.findById(requestId).orElseThrow();
        assertEquals(JoinRequestApprovalStatus.Approved, approvedReq.getApprovalStatus());

        // 5. Member entity exists in genealogy members table under the target branch
        var memberRows = entityManager.createNativeQuery(
                "SELECT member_id, full_name, branch_id, generation, status FROM members WHERE member_id = '" +
                activeAccount.getMemberId() + "'"
        ).getResultList();
        assertEquals(1, memberRows.size());
        Object[] memberRow = (Object[]) memberRows.get(0);
        assertEquals("Nguyễn Văn Hậu Duệ", memberRow[1]);
        assertEquals(ROOT_BRANCH_ID, memberRow[2]);
        assertEquals(3, ((Number) memberRow[3]).intValue());
        assertEquals("Living", memberRow[4]);

        // 6. Tree API reflects the newly added member
        mockMvc.perform(get("/api/v1/branches/tree")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("E2E Scenario 2: Prospective member proposes NewChildBranch and becomes representative on approval")
    @WithMockUser(username = CLAN_HEAD_ACCOUNT_ID, roles = {"CLAN_HEAD"})
    void e2e_JoinNewChildBranch_FullFlow_Success() throws Exception {
        // 1. Create invite code
        String createInvitePayload = """
                {
                    "targetBranchId": "BR_ROOT_E2E",
                    "maxUses": 5,
                    "validityDays": 10
                }
                """;

        MvcResult inviteRes = mockMvc.perform(post("/api/v1/invitations/general")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createInvitePayload))
                .andExpect(status().isCreated())
                .andReturn();

        String inviteCode = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).at("/data/inviteCode").asText();

        // 2. Submit join request with NewChildBranch
        String joinPayload = """
                {
                    "phoneNumber": "0912666555",
                    "password": "Password123@",
                    "email": "branch.pioneer@familyfund.vn",
                    "fullName": "Nguyễn Văn Tiên Phong",
                    "dateOfBirth": "1990-11-20",
                    "gender": "Male",
                    "proposalType": "NewChildBranch",
                    "targetBranchId": "BR_ROOT_E2E",
                    "proposedNewBranchName": "Chi Nhánh Đệ Nhị Hải Ngoại",
                    "proposedGeneration": 2,
                    "relationshipNote": "Đề xuất mở rộng chi nhánh mới tại phương xa"
                }
                """;

        MvcResult joinRes = mockMvc.perform(post("/api/v1/invitations/" + inviteCode + "/join")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.proposalType").value("NewChildBranch"))
                .andExpect(jsonPath("$.data.proposedNewBranchName").value("Chi Nhánh Đệ Nhị Hải Ngoại"))
                .andReturn();

        String requestId = objectMapper.readTree(joinRes.getResponse().getContentAsString()).at("/data/requestId").asText();
        String accountId = objectMapper.readTree(joinRes.getResponse().getContentAsString()).at("/data/accountId").asText();

        // 3. Clan Head approves request
        mockMvc.perform(post("/api/v1/join-requests/" + requestId + "/approve")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("Approved"));

        // 4. Verify in DB: child branch created and representative assigned
        UserAccount account = userAccountRepository.findById(accountId).orElseThrow();
        assertEquals(AccountStatus.Active, account.getAccountStatus());
        String memberId = account.getMemberId();
        assertNotNull(memberId);

        var childBranches = entityManager.createNativeQuery(
                "SELECT branch_id, branch_name, generation_level, parent_branch_id, representative_id " +
                "FROM family_branches WHERE parent_branch_id = '" + ROOT_BRANCH_ID + "'"
        ).getResultList();
        assertFalse(childBranches.isEmpty());
        Object[] branchRow = (Object[]) childBranches.get(0);
        assertEquals("Chi Nhánh Đệ Nhị Hải Ngoại", branchRow[1]);
        assertEquals(2, ((Number) branchRow[2]).intValue());
        assertEquals(ROOT_BRANCH_ID, branchRow[3]);
        assertEquals(memberId, branchRow[4], "New member should be assigned as branch representative");
    }

    @Test
    @DisplayName("E2E Scenario 3: Clan Head rejects join proposal with reason, temporary account is deactivated")
    @WithMockUser(username = CLAN_HEAD_ACCOUNT_ID, roles = {"CLAN_HEAD"})
    void e2e_RejectJoinRequest_DeactivatesAccount() throws Exception {
        // 1. Create invite code
        String createInvitePayload = """
                {
                    "targetBranchId": "BR_ROOT_E2E",
                    "maxUses": 5,
                    "validityDays": 10
                }
                """;

        MvcResult inviteRes = mockMvc.perform(post("/api/v1/invitations/general")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createInvitePayload))
                .andExpect(status().isCreated())
                .andReturn();

        String inviteCode = objectMapper.readTree(inviteRes.getResponse().getContentAsString()).at("/data/inviteCode").asText();

        // 2. Submit join request
        String joinPayload = """
                {
                    "phoneNumber": "0912444333",
                    "password": "Password123@",
                    "email": "suspicious@fake.com",
                    "fullName": "Lê Văn Lạ",
                    "dateOfBirth": "1999-01-01",
                    "gender": "Other",
                    "proposalType": "ExistingBranchLeaf",
                    "targetBranchId": "BR_ROOT_E2E",
                    "proposedGeneration": 10,
                    "relationshipNote": "Không rõ lai lịch"
                }
                """;

        MvcResult joinRes = mockMvc.perform(post("/api/v1/invitations/" + inviteCode + "/join")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String requestId = objectMapper.readTree(joinRes.getResponse().getContentAsString()).at("/data/requestId").asText();
        String accountId = objectMapper.readTree(joinRes.getResponse().getContentAsString()).at("/data/accountId").asText();

        // 3. Clan Head rejects proposal
        String rejectPayload = """
                {
                    "rejectionReason": "Hồ sơ không khớp với phả hệ dòng họ Nguyễn"
                }
                """;

        mockMvc.perform(post("/api/v1/join-requests/" + requestId + "/reject")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.approvalStatus").value("Rejected"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Hồ sơ không khớp với phả hệ dòng họ Nguyễn"));

        // 4. Verify in DB: account is Deactivated and request is Rejected
        UserAccount rejectedAccount = userAccountRepository.findById(accountId).orElseThrow();
        assertEquals(AccountStatus.Deactivated, rejectedAccount.getAccountStatus());
        assertNull(rejectedAccount.getMemberId());

        BranchJoinRequest rejectedReq = branchJoinRequestRepository.findById(requestId).orElseThrow();
        assertEquals(JoinRequestApprovalStatus.Rejected, rejectedReq.getApprovalStatus());
        assertEquals("Hồ sơ không khớp với phả hệ dòng họ Nguyễn", rejectedReq.getRejectionReason());

        // 5. Invitation counter was NOT incremented on rejection
        var invitation = invitationCodeRepository.findById(inviteCode).orElseThrow();
        assertEquals(0, invitation.getUsedCount());
    }

    @Test
    @DisplayName("E2E Scenario 4: Unauthorized user cannot view or approve join requests")
    void e2e_UnauthorizedAccess_Rejected() throws Exception {
        // Unauthenticated access to /api/v1/join-requests should be rejected
        mockMvc.perform(get("/api/v1/join-requests")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isUnauthorized());

        // Unauthenticated access to approve should be rejected
        mockMvc.perform(post("/api/v1/join-requests/REQ_DUMMY/approve")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isUnauthorized());
    }
}
