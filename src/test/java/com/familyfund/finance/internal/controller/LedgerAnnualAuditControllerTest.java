package com.familyfund.finance.internal.controller;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.LedgerImmutabilityViolationException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.api.dto.LedgerAnnualAuditStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.response.ImmutableLedgerEntryResponse;
import com.familyfund.finance.internal.dto.response.LedgerAdjustmentRequestResponse;
import com.familyfund.finance.internal.dto.response.LedgerAnnualAuditResponse;
import com.familyfund.finance.internal.service.LedgerAnnualAuditService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LedgerAnnualAuditControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LedgerAnnualAuditService ledgerAnnualAuditService;

    @InjectMocks
    private LedgerAnnualAuditController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private LedgerAnnualAuditResponse matchedSample() {
        ImmutableLedgerEntryResponse closingEntry = new ImmutableLedgerEntryResponse(
                "LDG_CLOSE_01", "CLN_01", null, null, null, null,
                LedgerTransactionType.OpeningBalanceCarryForward,
                new BigDecimal("0.00"),
                new BigDecimal("100000000.00"),
                "Chốt kiểm toán năm 2026",
                "ACC_HEAD", "ACC_HEAD",
                "a".repeat(64), "b".repeat(64),
                Instant.parse("2026-12-31T10:00:00Z")
        );
        return new LedgerAnnualAuditResponse(
                "AUD_01", "CLN_01", 2026, 2027,
                Instant.parse("2026-12-31T10:00:00Z"),
                new BigDecimal("100000000.00"),
                new BigDecimal("100000000.00"),
                new BigDecimal("0.00"),
                LedgerAnnualAuditStatus.Matched,
                "LDG_BASE_01",
                null,
                "LDG_CLOSE_01",
                new BigDecimal("100000000.00"),
                "ACC_HEAD",
                "Cụ Trưởng Tộc",
                "Khớp két sắt",
                null,
                Instant.parse("2026-12-31T10:00:00Z"),
                null,
                closingEntry
        );
    }

    private LedgerAnnualAuditResponse discrepancySample() {
        LedgerAdjustmentRequestResponse adj = new LedgerAdjustmentRequestResponse(
                "LAR_01", "LDG_BASE_01", "CLN_01", "EVT_01",
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"),
                new BigDecimal("-5000000.00"),
                "Tự động sinh từ kiểm toán",
                null, "ACC_HEAD", LedgerAdjustmentStatus.Pending,
                null, null, Instant.parse("2026-12-30T10:00:00Z"), null
        );
        return new LedgerAnnualAuditResponse(
                "AUD_02", "CLN_01", 2026, 2027,
                Instant.parse("2026-12-30T10:00:00Z"),
                new BigDecimal("100000000.00"),
                new BigDecimal("95000000.00"),
                new BigDecimal("-5000000.00"),
                LedgerAnnualAuditStatus.DiscrepancyDetected,
                "LDG_BASE_01",
                "LAR_01",
                null,
                null,
                "ACC_HEAD",
                "Cụ Trưởng Tộc",
                "Thiếu 5 triệu",
                "https://cdn/doc.pdf",
                Instant.parse("2026-12-30T10:00:00Z"),
                adj,
                null
        );
    }

    @Test
    @DisplayName("POST /api/ledger/annual-audit and /api/v1/ledger/annual-audit accept standard and alias payloads")
    void performAnnualAudit_bothRoutesAndAliases_return201() throws Exception {
        when(ledgerAnnualAuditService.performAnnualAudit(any())).thenReturn(matchedSample());

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualBalance": 100000000.00,
                                  "auditYear": 2026,
                                  "notes": "Khớp két sắt"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auditStatus").value("Matched"))
                .andExpect(jsonPath("$.data.closingLedgerEntry.transactionType").value("OpeningBalanceCarryForward"));

        when(ledgerAnnualAuditService.performAnnualAudit(any())).thenReturn(discrepancySample());

        mockMvc.perform(post("/api/v1/ledger/annual-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actual_balance": 95000000.00,
                                  "audit_year": 2026,
                                  "reference_transaction_id": "LDG_BASE_01",
                                  "audit_notes": "Thiếu 5 triệu",
                                  "evidence_attachment_url": "https://cdn/doc.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auditStatus").value("DiscrepancyDetected"))
                .andExpect(jsonPath("$.data.autoGeneratedAdjustmentRequest.requestId").value("LAR_01"));
    }

    @Test
    @DisplayName("Bean validation rejects null, negative, >2 decimals, and out-of-range auditYear with 400")
    void performAnnualAudit_validationErrors_return400() throws Exception {
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualBalance": -1000.00, "auditYear": 2026}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualBalance": 100.123, "auditYear": 2026}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualBalance": 100.00, "auditYear": 1999}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ledgerAnnualAuditService);
    }

    @Test
    @DisplayName("GET /annual-audits, GET /annual-audits/{auditId} and service error status mappings work properly")
    void getAndErrorMappings_returnExpectedHttpStatuses() throws Exception {
        when(ledgerAnnualAuditService.searchAnnualAudits(2026, LedgerAnnualAuditStatus.Matched))
                .thenReturn(List.of(matchedSample()));

        mockMvc.perform(get("/api/ledger/annual-audits")
                        .param("auditYear", "2026")
                        .param("status", "Matched"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].auditId").value("AUD_01"));

        verify(ledgerAnnualAuditService).searchAnnualAudits(2026, LedgerAnnualAuditStatus.Matched);

        when(ledgerAnnualAuditService.getAnnualAuditById("AUD_01")).thenReturn(matchedSample());
        mockMvc.perform(get("/api/v1/ledger/annual-audits/AUD_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditId").value("AUD_01"));

        mockMvc.perform(get("/api/ledger/annual-audits").param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Matched, DiscrepancyDetected")));

        when(ledgerAnnualAuditService.getAnnualAuditById("MISSING"))
                .thenThrow(new ResourceNotFoundException("Biên bản kiểm toán", "auditId", "MISSING"));
        mockMvc.perform(get("/api/ledger/annual-audits/MISSING"))
                .andExpect(status().isNotFound());

        org.mockito.Mockito.doThrow(new DuplicateResourceException("Đã chốt khớp năm 2026"))
                .when(ledgerAnnualAuditService).performAnnualAudit(any());
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100.00}"))
                .andExpect(status().isConflict());

        org.mockito.Mockito.doThrow(new LedgerImmutabilityViolationException("Gãy chuỗi SHA-256"))
                .when(ledgerAnnualAuditService).performAnnualAudit(any());
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(LedgerImmutabilityViolationException.ERROR_CODE));

        org.mockito.Mockito.doThrow(new BusinessRuleException("Còn đề xuất Pending"))
                .when(ledgerAnnualAuditService).performAnnualAudit(any());
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100.00}"))
                .andExpect(status().isBadRequest());
    }
}
