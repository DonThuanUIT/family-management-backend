package com.familyfund.finance.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.finance.internal.dto.response.LedgerIntegrityReportResponse;
import com.familyfund.finance.internal.service.ImmutableLedgerIntegrityService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImmutableLedgerIntegrityControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ImmutableLedgerIntegrityService integrityService;

    @InjectMocks
    private ImmutableLedgerIntegrityController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/ledger/verify-integrity và /api/v1/ledger/verify-integrity trả về báo cáo toàn vẹn 200 OK")
    void verifyIntegrity_returns200() throws Exception {
        LedgerIntegrityReportResponse validReport = new LedgerIntegrityReportResponse(
                "CLN_01", true, 5, 5, "GENESIS",
                "LDG_05", "f".repeat(64), List.of(), Instant.parse("2026-11-23T02:00:00Z")
        );
        when(integrityService.verifyCurrentClanChain()).thenReturn(validReport);

        mockMvc.perform(get("/api/ledger/verify-integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalEntries").value(5))
                .andExpect(jsonPath("$.data.verifiedEntries").value(5))
                .andExpect(jsonPath("$.data.genesisAnchor").value("GENESIS"));

        mockMvc.perform(get("/api/v1/ledger/verify-integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestTransactionId").value("LDG_05"));
    }
}
