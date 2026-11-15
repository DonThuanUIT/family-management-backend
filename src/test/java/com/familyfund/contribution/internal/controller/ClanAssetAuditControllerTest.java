package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.request.AuditClanAssetRequest;
import com.familyfund.contribution.internal.dto.response.AuditClanAssetResultResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetAuditResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetResponse;
import com.familyfund.contribution.internal.service.ClanAssetService;
import com.familyfund.core.exception.GlobalExceptionHandler;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClanAssetAuditControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClanAssetService clanAssetService;

    @InjectMocks
    private ClanAssetController clanAssetController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(clanAssetController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("PUT /api/v1/clan-assets/{id}/audit - 200 OK (Task #183)")
    void auditAsset_success() throws Exception {
        ClanAssetResponse asset = new ClanAssetResponse(
                "AST01", "Bộ lư đồng", 1, "bộ", BigDecimal.valueOf(5000000),
                "Chính điện", "CTB01", "CLAN01", "Good", Instant.now(), "ACC01", Instant.now()
        );
        ClanAssetAuditResponse audit = new ClanAssetAuditResponse(
                "AUD01", "AST01", Instant.now(), 1, 1, "Good", "Good",
                "Chính điện", "ACC01", "Định kỳ kiểm kê", Instant.now()
        );
        AuditClanAssetResultResponse response = new AuditClanAssetResultResponse(
                asset, audit, "Clan asset inventory audit recorded successfully"
        );

        when(clanAssetService.auditAsset(eq("AST01"), any(AuditClanAssetRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/clan-assets/AST01/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualQuantity\": 1, \"conditionStatus\": \"Good\", \"storageLocation\": \"Chính điện\", \"notes\": \"Định kỳ kiểm kê\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.asset.assetId").value("AST01"))
                .andExpect(jsonPath("$.data.auditLog.auditId").value("AUD01"))
                .andExpect(jsonPath("$.data.auditLog.conditionStatus").value("Good"));
    }

    @Test
    @DisplayName("GET /api/v1/clan-assets/{id}/audits - 200 OK (Task #183)")
    void getAssetAuditHistory_success() throws Exception {
        ClanAssetAuditResponse audit = new ClanAssetAuditResponse(
                "AUD01", "AST01", Instant.now(), 1, 1, "Good", "Good",
                "Chính điện", "ACC01", "Định kỳ kiểm kê", Instant.now()
        );

        when(clanAssetService.getAssetAuditHistory("AST01")).thenReturn(List.of(audit));

        mockMvc.perform(get("/api/v1/clan-assets/AST01/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].auditId").value("AUD01"));
    }
}
