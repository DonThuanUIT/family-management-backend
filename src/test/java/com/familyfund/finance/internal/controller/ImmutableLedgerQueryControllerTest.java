package com.familyfund.finance.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.response.LedgerFlowSummaryResponse;
import com.familyfund.finance.internal.dto.response.LedgerHistoryItemResponse;
import com.familyfund.finance.internal.dto.response.LedgerHistoryPageResponse;
import com.familyfund.finance.internal.dto.response.LinkedLedgerTransactionDto;
import com.familyfund.finance.internal.service.ImmutableLedgerQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ImmutableLedgerQueryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ImmutableLedgerQueryService queryService;

    @InjectMocks
    private ImmutableLedgerQueryController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private LedgerHistoryItemResponse sampleItem() {
        LinkedLedgerTransactionDto adjDto = new LinkedLedgerTransactionDto(
                "LDG_ADJ_01",
                LedgerTransactionType.AdjustmentTransaction,
                new BigDecimal("-5000000.00"),
                new BigDecimal("95000000.00"),
                "Bù trừ sai lệch",
                "LAR_01",
                "Nhập dư số tiền",
                "https://cdn.familyfund.vn/adj1.pdf",
                "ACC_TR",
                "Thủ Quỹ",
                "ACC_HEAD",
                "Trưởng Tộc",
                Instant.parse("2026-11-23T03:00:00Z")
        );
        return new LedgerHistoryItemResponse(
                "LDG_BASE_01",
                "CLN_01",
                "EVT_01",
                "Lễ Giỗ Tổ",
                null,
                null,
                null,
                null,
                null,
                (CommonFundExpenseCategory) null,
                null,
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"),
                new BigDecimal("100000000.00"),
                "Kết dư sự kiện",
                "ACC_TR",
                "Thủ Quỹ",
                "ACC_HEAD",
                "Trưởng Tộc",
                "GENESIS",
                "a".repeat(64),
                Instant.parse("2026-11-23T02:00:00Z"),
                false,
                true,
                1,
                new BigDecimal("95000000.00"),
                0,
                null,
                List.of(adjDto)
        );
    }

    private LedgerHistoryPageResponse samplePage() {
        LedgerFlowSummaryResponse summary = new LedgerFlowSummaryResponse(
                new BigDecimal("95000000.00"),
                "LDG_ADJ_01",
                Instant.parse("2026-11-23T03:00:00Z"),
                new BigDecimal("0.00"),
                new BigDecimal("95000000.00"),
                1L,
                new BigDecimal("100000000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("100000000.00")
        );
        return new LedgerHistoryPageResponse(
                "CLN_01",
                List.of(sampleItem()),
                0,
                20,
                1L,
                1,
                summary
        );
    }

    @Test
    @DisplayName("GET /api/ledger, /api/v1/ledger và /api/ledger/immutable trả về trang lịch sử Sổ cái 200 OK kèm liên kết 2 chiều")
    void searchLedgerHistory_allAliases_return200() throws Exception {
        LedgerHistoryPageResponse pageResponse = samplePage();
        when(queryService.searchLedgerHistory(
                "2026-11-01",
                "2026-11-30",
                List.of(LedgerTransactionType.SurplusTransfer),
                "EVT_01",
                "Kết dư",
                true,
                "ASC",
                0,
                20
        )).thenReturn(pageResponse);

        for (String path : List.of("/api/ledger", "/api/v1/ledger", "/api/ledger/immutable", "/api/v1/ledger/immutable")) {
            mockMvc.perform(get(path)
                            .param("from", "2026-11-01")
                            .param("to", "2026-11-30")
                            .param("transactionType", "SurplusTransfer")
                            .param("eventId", "EVT_01")
                            .param("keyword", "Kết dư")
                            .param("onlyWithAdjustments", "true")
                            .param("sortDirection", "ASC")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.clanId").value("CLN_01"))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.items[0].transactionId").value("LDG_BASE_01"))
                    .andExpect(jsonPath("$.data.items[0].hasAdjustments").value(true))
                    .andExpect(jsonPath("$.data.items[0].adjustmentCount").value(1))
                    .andExpect(jsonPath("$.data.items[0].netAdjustedAmount").value(95000000.00))
                    .andExpect(jsonPath("$.data.items[0].adjustmentTransactions[0].transactionId").value("LDG_ADJ_01"))
                    .andExpect(jsonPath("$.data.summary.currentFundBalance").value(95000000.00));
        }
    }

    @Test
    @DisplayName("GET /api/ledger với transactionType=INVALID trả về 400 Bad Request")
    void searchLedgerHistory_invalidEnum_returns400() throws Exception {
        mockMvc.perform(get("/api/ledger")
                        .param("transactionType", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/ledger")
                        .param("transactionType", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/ledger/transactions/{transactionId} và /api/v1/ledger/transactions/{transactionId} trả về chi tiết giao dịch hoặc 404")
    void getTransactionDetail_returns200Or404() throws Exception {
        when(queryService.getTransactionDetail("LDG_BASE_01")).thenReturn(sampleItem());
        when(queryService.getTransactionDetail("LDG_MISSING"))
                .thenThrow(new ResourceNotFoundException("Giao dịch sổ cái", "transactionId", "LDG_MISSING"));

        mockMvc.perform(get("/api/ledger/transactions/LDG_BASE_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionId").value("LDG_BASE_01"))
                .andExpect(jsonPath("$.data.adjustmentTransactions[0].transactionId").value("LDG_ADJ_01"));

        mockMvc.perform(get("/api/v1/ledger/transactions/LDG_BASE_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value("LDG_BASE_01"));

        mockMvc.perform(get("/api/ledger/transactions/LDG_MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        verify(queryService).getTransactionDetail("LDG_MISSING");
    }
}
