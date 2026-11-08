package com.familyfund.core.audit.controller;

import com.familyfund.core.audit.dto.AuditLogPageResponse;
import com.familyfund.core.audit.dto.SystemAuditLogResponse;
import com.familyfund.core.audit.model.AuditAction;
import com.familyfund.core.audit.model.AuditEntityType;
import com.familyfund.core.audit.service.AuditQueryService;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuditQueryService auditQueryService;

    @InjectMocks
    private AuditLogController auditLogController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(auditLogController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private SystemAuditLogResponse sampleLog() {
        return new SystemAuditLogResponse(
                "AUD_TEST_01",
                "CLAN_DEFAULT",
                "ACC_ADMIN",
                "Nguyễn Văn Trưởng",
                AuditAction.UPDATE.name(),
                AuditEntityType.BRANCH.name(),
                "BR_01",
                "ROLE_BRANCH_HEAD",
                "Trưởng Chi / Nhánh",
                "BRANCH",
                "BR_01",
                "Nhánh Trưởng",
                "MEM_01",
                "Nguyễn Văn A",
                "ACC_01",
                "Cập nhật thông tin chi nhánh",
                Map.of("branchName", "Cũ"),
                Map.of("branchName", "Mới"),
                "127.0.0.1",
                Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/audit-logs - Should return 200 and audit log page wrapped in ApiResponse")
    void shouldReturnPage() throws Exception {
        AuditLogPageResponse pageResponse = new AuditLogPageResponse(
                List.of(sampleLog()),
                0,
                20,
                1L,
                1
        );

        when(auditQueryService.getAuditLogs(anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("page", "0")
                        .param("size", "20")
                        .param("actionType", "UPDATE")
                        .param("entityType", "BRANCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].auditId").value("AUD_TEST_01"))
                .andExpect(jsonPath("$.data.items[0].actionType").value("UPDATE"))
                .andExpect(jsonPath("$.data.items[0].entityType").value("BRANCH"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/audit-logs/{auditId} - Should return 200 and audit log detail wrapped in ApiResponse")
    void shouldReturnDetail() throws Exception {
        when(auditQueryService.getAuditLogDetail(eq("AUD_TEST_01"))).thenReturn(sampleLog());

        mockMvc.perform(get("/api/v1/audit-logs/AUD_TEST_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auditId").value("AUD_TEST_01"))
                .andExpect(jsonPath("$.data.actorFullName").value("Nguyễn Văn Trưởng"));
    }

    @Test
    @DisplayName("GET /api/v1/audit-logs/{auditId} - Should return 404 when auditId does not exist")
    void shouldReturn404WhenNotFound() throws Exception {
        when(auditQueryService.getAuditLogDetail(eq("AUD_NONEXIST")))
                .thenThrow(new ResourceNotFoundException("AuditLog not found"));

        mockMvc.perform(get("/api/v1/audit-logs/AUD_NONEXIST"))
                .andExpect(status().isNotFound());
    }
}
