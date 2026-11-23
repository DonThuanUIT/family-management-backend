package com.familyfund.finance.internal.controller;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.request.ProposeCommonFundDisbursementRequest;
import com.familyfund.finance.internal.dto.request.ReviewCommonFundDisbursementRequest;
import com.familyfund.finance.internal.dto.response.CommonFundBalanceResponse;
import com.familyfund.finance.internal.dto.response.CommonFundDisbursementResponse;
import com.familyfund.finance.internal.dto.response.ImmutableLedgerEntryResponse;
import com.familyfund.finance.internal.dto.response.ReviewCommonFundDisbursementResponse;
import com.familyfund.finance.internal.service.CommonFundDisbursementService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommonFundDisbursementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CommonFundDisbursementService commonFundDisbursementService;

    @InjectMocks
    private CommonFundDisbursementController controller;

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
    @DisplayName("GET /api/ledger/balance & /api/v1/ledger/balance trả về số dư khả dụng 200 OK")
    void getFundBalance_returns200() throws Exception {
        Instant now = Instant.parse("2026-11-22T08:00:00Z");
        CommonFundBalanceResponse response = new CommonFundBalanceResponse(
                "CLN_01", new BigDecimal("100000000.00"), new BigDecimal("15000000.00"),
                "LDG_LATEST", now
        );

        when(commonFundDisbursementService.getFundBalance()).thenReturn(response);

        mockMvc.perform(get("/api/ledger/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.availableBalance").value(100000000.00))
                .andExpect(jsonPath("$.data.pendingDisbursementAmount").value(15000000.00));

        mockMvc.perform(get("/api/v1/ledger/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestTransactionId").value("LDG_LATEST"));
    }

    @Test
    @DisplayName("POST /api/ledger/disbursements & /api/v1/ledger/disbursements (camelCase + snake_case) trả về 201 Created")
    void proposeDisbursement_returns201() throws Exception {
        Instant now = Instant.parse("2026-11-22T08:00:00Z");
        CommonFundDisbursementResponse response = new CommonFundDisbursementResponse(
                "CFD0001TEST", "CLN_01", "Tu sửa mái ngói Từ đường",
                CommonFundExpenseCategory.TempleMaintenance, new BigDecimal("45000000.00"),
                "Đội thợ nề Đồng Kỵ", null, "Thay thế ngói âm dương dột nát",
                "https://storage.familyfund.vn/disbursements/quote.pdf",
                "ACC_TREASURER", CommonFundDisbursementStatus.Pending, null, null, null, now, null
        );

        when(commonFundDisbursementService.proposeDisbursement(any(ProposeCommonFundDisbursementRequest.class)))
                .thenReturn(response);

        String camelJson = """
                {
                    "title": "Tu sửa mái ngói Từ đường",
                    "category": "TempleMaintenance",
                    "amount": 45000000.00,
                    "beneficiaryName": "Đội thợ nề Đồng Kỵ",
                    "purpose": "Thay thế ngói âm dương dột nát",
                    "attachmentUrl": "https://storage.familyfund.vn/disbursements/quote.pdf"
                }
                """;

        mockMvc.perform(post("/api/ledger/disbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(camelJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value("CFD0001TEST"))
                .andExpect(jsonPath("$.data.approvalStatus").value("Pending"));

        String snakeJson = """
                {
                    "title": "Tu sửa mái ngói Từ đường",
                    "expense_category": "TempleMaintenance",
                    "amount": 45000000.00,
                    "beneficiary_name": "Đội thợ nề Đồng Kỵ",
                    "purpose": "Thay thế ngói âm dương dột nát",
                    "attachment_url": "https://storage.familyfund.vn/disbursements/quote.pdf"
                }
                """;

        mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(snakeJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(45000000.00));
    }

    @Test
    @DisplayName("POST /api/ledger/disbursements/{id}/review (approve) & /review-disbursement (reject) trả về 200 OK")
    void reviewDisbursement_approveAndReject_returns200() throws Exception {
        Instant now = Instant.parse("2026-11-22T08:10:00Z");
        CommonFundDisbursementResponse approvedDto = new CommonFundDisbursementResponse(
                "CFD0001TEST", "CLN_01", "Tu sửa mái ngói Từ đường",
                CommonFundExpenseCategory.TempleMaintenance, new BigDecimal("45000000.00"),
                "Đội thợ nề Đồng Kỵ", null, "Thay thế ngói âm dương dột nát",
                null, "ACC_TREASURER", CommonFundDisbursementStatus.Approved,
                "ACC_HEAD", null, "LDG0001CFD", now, now
        );
        ImmutableLedgerEntryResponse ledgerDto = new ImmutableLedgerEntryResponse(
                "LDG0001CFD", "CLN_01", null, null, "CFD0001TEST", null,
                LedgerTransactionType.CommonFundDisbursement, new BigDecimal("-45000000.00"),
                new BigDecimal("55000000.00"), "Chi tu sửa mái ngói",
                "ACC_TREASURER", "ACC_HEAD", "GENESIS", "b".repeat(64), now
        );

        when(commonFundDisbursementService.reviewDisbursement(
                eq("CFD0001TEST"), any(ReviewCommonFundDisbursementRequest.class)))
                .thenReturn(new ReviewCommonFundDisbursementResponse(approvedDto, ledgerDto));

        mockMvc.perform(post("/api/ledger/disbursements/CFD0001TEST/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "approved": true,
                                    "confirmPassword": "ClanHead@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.disbursementRequest.approvalStatus").value("Approved"))
                .andExpect(jsonPath("$.data.ledgerEntry.transactionType").value("CommonFundDisbursement"))
                .andExpect(jsonPath("$.data.ledgerEntry.amount").value(-45000000.00))
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(55000000.00));

        CommonFundDisbursementResponse rejectedDto = new CommonFundDisbursementResponse(
                "CFD0002TEST", "CLN_01", "Chi lễ hội",
                CommonFundExpenseCategory.Ceremony, new BigDecimal("10000000.00"),
                null, null, "Tổ chức lễ hội", null, "ACC_TREASURER",
                CommonFundDisbursementStatus.Rejected, "ACC_HEAD",
                "Chưa có báo giá đính kèm", null, now, now
        );

        when(commonFundDisbursementService.reviewDisbursement(
                isNull(), any(ReviewCommonFundDisbursementRequest.class)))
                .thenReturn(new ReviewCommonFundDisbursementResponse(rejectedDto, null));

        mockMvc.perform(post("/api/v1/ledger/review-disbursement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_id": "CFD0002TEST",
                                    "approved": false,
                                    "rejection_reason": "Chưa có báo giá đính kèm"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbursementRequest.approvalStatus").value("Rejected"))
                .andExpect(jsonPath("$.data.ledgerEntry").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("Validation 400 và mapping ngoại lệ BusinessRuleException (400) / ResourceNotFoundException (404)")
    void validationAndExceptions_mappedProperly() throws Exception {
        mockMvc.perform(post("/api/ledger/disbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "",
                                    "amount": 0,
                                    "purpose": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        when(commonFundDisbursementService.proposeDisbursement(any(ProposeCommonFundDisbursementRequest.class)))
                .thenThrow(new BusinessRuleException("Số dư khả dụng Quỹ chung không đủ"));

        mockMvc.perform(post("/api/ledger/disbursements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Chi khuyến học",
                                    "category": "Scholarship",
                                    "amount": 200000000.00,
                                    "purpose": "Khen thưởng cuối năm"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        when(commonFundDisbursementService.getDisbursementById("CFD_404"))
                .thenThrow(new ResourceNotFoundException("Đề xuất chi Quỹ chung", "requestId", "CFD_404"));

        mockMvc.perform(get("/api/ledger/disbursements/CFD_404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        Instant now = Instant.parse("2026-11-22T08:00:00Z");
        when(commonFundDisbursementService.searchDisbursements(
                CommonFundDisbursementStatus.Pending, CommonFundExpenseCategory.Scholarship))
                .thenReturn(List.of(new CommonFundDisbursementResponse(
                        "CFD_003", "CLN_01", "Học bổng", CommonFundExpenseCategory.Scholarship,
                        new BigDecimal("5000000.00"), "Nguyễn A", "MEM_1", "Khuyến học", null,
                        "ACC_1", CommonFundDisbursementStatus.Pending, null, null, null, now, null
                )));

        mockMvc.perform(get("/api/ledger/disbursements")
                        .param("status", "Pending")
                        .param("category", "Scholarship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requestId").value("CFD_003"));
    }
}
