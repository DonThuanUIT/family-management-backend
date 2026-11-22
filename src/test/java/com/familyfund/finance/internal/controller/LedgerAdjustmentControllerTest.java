package com.familyfund.finance.internal.controller;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.request.ProposeLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.request.ReviewLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.response.ImmutableLedgerEntryResponse;
import com.familyfund.finance.internal.dto.response.LedgerAdjustmentRequestResponse;
import com.familyfund.finance.internal.dto.response.ReviewLedgerAdjustmentResponse;
import com.familyfund.finance.internal.service.LedgerAdjustmentService;
import org.hamcrest.Matchers;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LedgerAdjustmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LedgerAdjustmentService ledgerAdjustmentService;

    @InjectMocks
    private LedgerAdjustmentController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/ledger/propose-adjustment: Tạo đề xuất bù trừ âm hợp lệ trả về 201 Created")
    void proposeAdjustment_validCamelCase_returns201() throws Exception {
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        LedgerAdjustmentRequestResponse response = new LedgerAdjustmentRequestResponse(
                "LAR0001TEST", "LDG_001", "CLN_01", "EVT_01",
                LedgerTransactionType.SurplusTransfer, new BigDecimal("50000000.00"),
                new BigDecimal("-5000000.00"), "Tính nhầm hóa đơn âm thanh",
                "https://storage.familyfund.vn/evidence/adj-001.pdf",
                "ACC_TREASURER", LedgerAdjustmentStatus.Pending, null, null, now, null
        );

        when(ledgerAdjustmentService.proposeAdjustment(any(ProposeLedgerAdjustmentRequest.class)))
                .thenReturn(response);

        String json = """
                {
                    "originalTransactionId": "LDG_001",
                    "proposedAdjustmentAmount": -5000000.00,
                    "adjustmentReason": "Tính nhầm hóa đơn âm thanh",
                    "evidenceAttachmentUrl": "https://storage.familyfund.vn/evidence/adj-001.pdf"
                }
                """;

        mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value("LAR0001TEST"))
                .andExpect(jsonPath("$.data.originalTransactionId").value("LDG_001"))
                .andExpect(jsonPath("$.data.proposedAdjustmentAmount").value(-5000000.00))
                .andExpect(jsonPath("$.data.approvalStatus").value("Pending"));
    }

    @Test
    @DisplayName("POST /api/v1/ledger/propose-adjustment: Hỗ trợ snake_case JSON payload và số dương (+)")
    void proposeAdjustment_v1AndSnakeCase_returns201() throws Exception {
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        LedgerAdjustmentRequestResponse response = new LedgerAdjustmentRequestResponse(
                "LAR0002TEST", "LDG_002", "CLN_01", "EVT_01",
                LedgerTransactionType.SurplusTransfer, new BigDecimal("20000000.00"),
                new BigDecimal("2500000.00"), "Bổ sung khoản hoàn trả nhà cung cấp",
                null, "ACC_TREASURER", LedgerAdjustmentStatus.Pending, null, null, now, null
        );

        when(ledgerAdjustmentService.proposeAdjustment(any(ProposeLedgerAdjustmentRequest.class)))
                .thenReturn(response);

        String snakeJson = """
                {
                    "original_transaction_id": "LDG_002",
                    "proposed_adjustment_amount": 2500000.00,
                    "adjustment_reason": "Bổ sung khoản hoàn trả nhà cung cấp"
                }
                """;

        mockMvc.perform(post("/api/v1/ledger/propose-adjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(snakeJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value("LAR0002TEST"))
                .andExpect(jsonPath("$.data.proposedAdjustmentAmount").value(2500000.00));
    }

    @Test
    @DisplayName("POST /api/ledger/propose-adjustment: Thiếu trường bắt buộc trả về 400 Bad Request")
    void proposeAdjustment_missingFields_returns400() throws Exception {
        String invalidJson = """
                {
                    "originalTransactionId": "",
                    "adjustmentReason": ""
                }
                """;

        mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/ledger/propose-adjustment: Số tiền bằng 0 hoặc trùng Pending trả về 400 Bad Request")
    void proposeAdjustment_businessRuleViolation_returns400() throws Exception {
        when(ledgerAdjustmentService.proposeAdjustment(any(ProposeLedgerAdjustmentRequest.class)))
                .thenThrow(new BusinessRuleException("Số tiền đề xuất bù trừ phải khác 0"));

        String zeroJson = """
                {
                    "originalTransactionId": "LDG_001",
                    "proposedAdjustmentAmount": 0.00,
                    "adjustmentReason": "Kiểm tra số tiền bằng 0"
                }
                """;

        mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(zeroJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/ledger/propose-adjustment: Không tìm thấy giao dịch gốc trả về 404 Not Found")
    void proposeAdjustment_originalTxNotFound_returns404() throws Exception {
        when(ledgerAdjustmentService.proposeAdjustment(any(ProposeLedgerAdjustmentRequest.class)))
                .thenThrow(new ResourceNotFoundException("Giao dịch sổ cái gốc", "originalTransactionId", "LDG_404"));

        String json = """
                {
                    "originalTransactionId": "LDG_404",
                    "proposedAdjustmentAmount": -1000000.00,
                    "adjustmentReason": "Sai giao dịch"
                }
                """;

        mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/ledger/adjustments & /adjustments/{id}: Tra cứu danh sách và chi tiết trả về 200 OK")
    void getAndSearchAdjustments_returns200() throws Exception {
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        LedgerAdjustmentRequestResponse response = new LedgerAdjustmentRequestResponse(
                "LAR0001TEST", "LDG_001", "CLN_01", "EVT_01",
                LedgerTransactionType.SurplusTransfer, new BigDecimal("50000000.00"),
                new BigDecimal("-5000000.00"), "Tính nhầm hóa đơn âm thanh",
                null, "ACC_TREASURER", LedgerAdjustmentStatus.Pending, null, null, now, null
        );

        when(ledgerAdjustmentService.getAdjustmentRequestById("LAR0001TEST")).thenReturn(response);
        when(ledgerAdjustmentService.searchAdjustmentRequests(eq("LDG_001"), eq(LedgerAdjustmentStatus.Pending)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/ledger/adjustments/LAR0001TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value("LAR0001TEST"));

        mockMvc.perform(get("/api/v1/ledger/adjustments")
                        .param("originalTransactionId", "LDG_001")
                        .param("status", "Pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].requestId").value("LAR0001TEST"));
    }

    @Test
    @DisplayName("POST /api/ledger/review-adjustment: Phê duyệt đề xuất (camelCase) trả về 200 OK kèm bút toán sổ cái")
    void reviewAdjustment_approveCamelCase_returns200() throws Exception {
        Instant now = Instant.parse("2026-11-22T07:20:00Z");
        LedgerAdjustmentRequestResponse reqDto = new LedgerAdjustmentRequestResponse(
                "LAR0001TEST", "LDG_001", "CLN_01", "EVT_01",
                LedgerTransactionType.SurplusTransfer, new BigDecimal("50000000.00"),
                new BigDecimal("-5000000.00"), "Tính nhầm hóa đơn âm thanh",
                "https://storage.familyfund.vn/evidence/adj-001.pdf",
                "ACC_TREASURER", LedgerAdjustmentStatus.Approved, "ACC_HEAD", null, now, now
        );
        ImmutableLedgerEntryResponse ledgerDto = new ImmutableLedgerEntryResponse(
                "LDG0001ADJ", "CLN_01", "EVT_01", "LAR0001TEST", null, "LDG_001",
                LedgerTransactionType.AdjustmentTransaction, new BigDecimal("-5000000.00"),
                new BigDecimal("165000000.00"), "Bù trừ giảm chi phí âm thanh",
                "ACC_TREASURER", "ACC_HEAD", "a".repeat(64), now
        );
        ReviewLedgerAdjustmentResponse response = new ReviewLedgerAdjustmentResponse(reqDto, ledgerDto);

        when(ledgerAdjustmentService.reviewAdjustment(any(ReviewLedgerAdjustmentRequest.class)))
                .thenReturn(response);

        String json = """
                {
                    "requestId": "LAR0001TEST",
                    "approved": true,
                    "description": "Bù trừ giảm chi phí âm thanh"
                }
                """;

        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adjustmentRequest.approvalStatus").value("Approved"))
                .andExpect(jsonPath("$.data.ledgerEntry.transactionId").value("LDG0001ADJ"))
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(165000000.00))
                .andExpect(jsonPath("$.data.ledgerEntry.recordHash").value("a".repeat(64)));
    }

    @Test
    @DisplayName("POST /api/v1/ledger/review-adjustment: Từ chối đề xuất (snake_case) trả về 200 OK và ledgerEntry = null")
    void reviewAdjustment_v1RejectSnakeCase_returns200() throws Exception {
        Instant now = Instant.parse("2026-11-22T07:20:00Z");
        LedgerAdjustmentRequestResponse reqDto = new LedgerAdjustmentRequestResponse(
                "LAR0002TEST", "LDG_002", "CLN_01", "EVT_01",
                LedgerTransactionType.SurplusTransfer, new BigDecimal("20000000.00"),
                new BigDecimal("-2000000.00"), "Sai sót hóa đơn",
                null, "ACC_TREASURER", LedgerAdjustmentStatus.Rejected, "ACC_HEAD",
                "Chứng từ chưa có chữ ký xác nhận", now, now
        );
        ReviewLedgerAdjustmentResponse response = new ReviewLedgerAdjustmentResponse(reqDto, null);

        when(ledgerAdjustmentService.reviewAdjustment(any(ReviewLedgerAdjustmentRequest.class)))
                .thenReturn(response);

        String snakeJson = """
                {
                    "request_id": "LAR0002TEST",
                    "approved": false,
                    "rejection_reason": "Chứng từ chưa có chữ ký xác nhận"
                }
                """;

        mockMvc.perform(post("/api/v1/ledger/review-adjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(snakeJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adjustmentRequest.approvalStatus").value("Rejected"))
                .andExpect(jsonPath("$.data.adjustmentRequest.rejectionReason")
                        .value("Chứng từ chưa có chữ ký xác nhận"))
                .andExpect(jsonPath("$.data.ledgerEntry").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("POST /api/ledger/review-adjustment: Thiếu requestId hoặc approved trả về 400 Bad Request")
    void reviewAdjustment_missingFields_returns400() throws Exception {
        String invalidJson = """
                {
                    "requestId": ""
                }
                """;

        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/ledger/review-adjustment: Lỗi nghiệp vụ trả về 400 và không tìm thấy trả về 404")
    void reviewAdjustment_exceptions_mappedProperly() throws Exception {
        when(ledgerAdjustmentService.reviewAdjustment(any(ReviewLedgerAdjustmentRequest.class)))
                .thenThrow(new BusinessRuleException("Đề xuất đã được xử lý trước đó"));

        String json = """
                {
                    "requestId": "LAR0001TEST",
                    "approved": true
                }
                """;

        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        when(ledgerAdjustmentService.reviewAdjustment(any(ReviewLedgerAdjustmentRequest.class)))
                .thenThrow(new ResourceNotFoundException("Đề xuất điều chỉnh sổ cái", "requestId", "LAR_404"));

        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "requestId": "LAR_404",
                                    "approved": true
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
