package com.familyfund.event.internal.controller;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.event.internal.dto.request.RoleAssignmentRequest;
import com.familyfund.event.internal.dto.request.RoleRevocationRequest;
import com.familyfund.event.internal.dto.response.AuditLogResponse;
import com.familyfund.event.internal.dto.response.BranchRoleOverviewDto;
import com.familyfund.event.internal.dto.response.MemberRoleOptionDto;
import com.familyfund.event.internal.dto.response.RoleAssignmentResponse;
import com.familyfund.event.internal.dto.response.RoleManagementOverviewResponse;
import com.familyfund.event.internal.service.RoleManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoleManagementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RoleManagementService roleManagementService;

    @InjectMocks
    private RoleManagementController roleManagementController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(roleManagementController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/roles/assign - Success 200 OK")
    void assignRole_Success() throws Exception {
        RoleAssignmentResponse response = new RoleAssignmentResponse(
                "AUD_123", "ASSIGN", "ROLE_BRANCH_HEAD", "Trưởng Chi / Nhánh",
                "BRANCH", "BR_01", "Chi 1", "MEM_01", "Nguyễn Văn A",
                "ACC_01", "ACC_ADMIN", "Bổ nhiệm", Instant.now()
        );
        when(roleManagementService.assignRole(any(RoleAssignmentRequest.class))).thenReturn(response);

        String json = """
                {
                    "memberId": "MEM_01",
                    "roleCode": "ROLE_BRANCH_HEAD",
                    "scopeType": "BRANCH",
                    "scopeId": "BR_01",
                    "description": "Bổ nhiệm"
                }
                """;

        mockMvc.perform(post("/api/v1/roles/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auditId").value("AUD_123"))
                .andExpect(jsonPath("$.data.roleCode").value("ROLE_BRANCH_HEAD"))
                .andExpect(jsonPath("$.data.targetMemberName").value("Nguyễn Văn A"));

        verify(roleManagementService).assignRole(any(RoleAssignmentRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/roles/assign - Validation failure (blank memberId) -> 400")
    void assignRole_ValidationFailure() throws Exception {
        String json = """
                {
                    "memberId": "",
                    "roleCode": "ROLE_BRANCH_HEAD",
                    "scopeType": "BRANCH",
                    "scopeId": "BR_01"
                }
                """;

        mockMvc.perform(post("/api/v1/roles/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/roles/revoke - Success 200 OK")
    void revokeRole_Success() throws Exception {
        RoleAssignmentResponse response = new RoleAssignmentResponse(
                "AUD_456", "REVOKE", "HeadOfOrganizer", "Trưởng Ban Tổ Chức",
                "EVENT", "EVT_01", "Giỗ Tổ", "MEM_01", "Nguyễn Văn A",
                "ACC_01", "ACC_ADMIN", "Thu hồi", Instant.now()
        );
        when(roleManagementService.revokeRole(any(RoleRevocationRequest.class))).thenReturn(response);

        String json = """
                {
                    "memberId": "MEM_01",
                    "roleCode": "HeadOfOrganizer",
                    "scopeType": "EVENT",
                    "scopeId": "EVT_01",
                    "reason": "Thay đổi nhân sự"
                }
                """;

        mockMvc.perform(post("/api/v1/roles/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auditId").value("AUD_456"))
                .andExpect(jsonPath("$.data.actionType").value("REVOKE"));

        verify(roleManagementService).revokeRole(any(RoleRevocationRequest.class));
    }

    @Test
    @DisplayName("GET /api/v1/roles/audit-logs - Success 200 OK")
    void getAuditLogs_Success() throws Exception {
        AuditLogResponse log = new AuditLogResponse(
                "AUD_1", "CLAN_01", "ACC_ADMIN", "Admin", "MEM_01", "Nguyễn Văn A",
                "ACC_01", "ASSIGN", "ROLE_BRANCH_HEAD", "Trưởng Chi / Nhánh",
                "BRANCH", "BR_01", "Chi 1", "Bổ nhiệm", Instant.now()
        );
        when(roleManagementService.getAuditLogs(null, null, null)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/roles/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].auditId").value("AUD_1"))
                .andExpect(jsonPath("$.data[0].roleDisplayName").value("Trưởng Chi / Nhánh"));
    }

    @Test
    @DisplayName("GET /api/v1/roles/overview - Success 200 OK")
    void getOverview_Success() throws Exception {
        BranchRoleOverviewDto branch = new BranchRoleOverviewDto("BR_01", "Chi 1", 1, "MEM_01", "Nguyễn Văn A", "ACC_01");
        MemberRoleOptionDto member = new MemberRoleOptionDto("MEM_01", "Nguyễn Văn A", "BR_01", "Chi 1", true, "ACC_01");
        RoleManagementOverviewResponse overview = new RoleManagementOverviewResponse("CLAN_01", List.of(branch), List.of(member));

        when(roleManagementService.getRoleManagementOverview()).thenReturn(overview);

        mockMvc.perform(get("/api/v1/roles/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.branches[0].branchName").value("Chi 1"))
                .andExpect(jsonPath("$.data.members[0].fullName").value("Nguyễn Văn A"));
    }
}
