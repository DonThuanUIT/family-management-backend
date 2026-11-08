package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.request.ReconcileContributionRequest;
import com.familyfund.contribution.internal.dto.response.ReconcileContributionResponse;
import com.familyfund.contribution.internal.dto.response.UnreconciledContributionResponse;
import com.familyfund.contribution.internal.service.CashContributionService;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
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
class ReconcileCashContributionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CashContributionService cashContributionService;

    @InjectMocks
    private CashContributionController cashContributionController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(cashContributionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/contributions/cash/unreconciled - 200 OK")
    void getUnreconciledContributions_success() throws Exception {
        UnreconciledContributionResponse item = new UnreconciledContributionResponse(
                "CTB_001", "PT-20261029-A1B2C3", "EVT_001", "Lễ Giỗ Tổ",
                "MEM_001", "Nguyễn Văn A", BigDecimal.valueOf(1000000), "Một triệu đồng chẵn",
                Instant.now(), null, null, false
        );

        when(cashContributionService.getUnreconciledContributions(null)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/contributions/cash/unreconciled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].contributionId").value("CTB_001"))
                .andExpect(jsonPath("$.data[0].isReconciled").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/contributions/cash/{id}/reconcile - 200 OK")
    void reconcileContribution_success() throws Exception {
        ReconcileContributionResponse response = new ReconcileContributionResponse(
                "CTB_001", "PT-20261029-A1B2C3", "FT26110912345", "https://proof.example.com/receipt.jpg",
                true, Instant.now(), "ACC_TREASURER", "Reconciled successfully"
        );

        when(cashContributionService.reconcileContribution(eq("CTB_001"), any(ReconcileContributionRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/contributions/cash/CTB_001/reconcile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankTransactionCode\": \"FT26110912345\", \"transferProofUrl\": \"https://proof.example.com/receipt.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isReconciled").value(true))
                .andExpect(jsonPath("$.data.bankTransactionCode").value("FT26110912345"));
    }

    @Test
    @DisplayName("PUT /api/v1/contributions/cash/{id}/reconcile - 404 Not Found")
    void reconcileContribution_notFound_returns404() throws Exception {
        when(cashContributionService.reconcileContribution(eq("CTB_999"), any(ReconcileContributionRequest.class)))
                .thenThrow(new ResourceNotFoundException("Contribution not found"));

        mockMvc.perform(put("/api/v1/contributions/cash/CTB_999/reconcile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankTransactionCode\": \"FT26110912345\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/contributions/cash/{id}/reconcile - 400 Bad Request on empty code")
    void reconcileContribution_emptyCode_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/contributions/cash/CTB_001/reconcile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankTransactionCode\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
