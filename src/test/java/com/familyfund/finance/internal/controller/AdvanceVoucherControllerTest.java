package com.familyfund.finance.internal.controller;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.internal.dto.request.ConfirmDisbursementRequest;
import com.familyfund.finance.internal.dto.request.CreateAdvanceVoucherRequest;
import com.familyfund.finance.internal.dto.response.AdvanceVoucherResponse;
import com.familyfund.finance.internal.service.AdvanceVoucherService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdvanceVoucherControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdvanceVoucherService advanceVoucherService;

    @InjectMocks
    private AdvanceVoucherController controller;

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
    @DisplayName("POST /api/v1/advances: Lập phiếu tạm ứng hợp lệ trả về 201 Created")
    void createAdvance_validRequest_returns201() throws Exception {
        Instant now = Instant.parse("2026-04-01T09:00:00Z");
        Instant deadline = Instant.parse("2026-04-15T17:00:00Z");

        AdvanceVoucherResponse response = new AdvanceVoucherResponse(
                "ADV001", "TSK_01", "Đặt cỗ", "EVT_01", "Lễ Giỗ", "CLN_01",
                "MEM_01", "Nguyễn Văn A", new BigDecimal("5000000.00"),
                now, deadline, "Cam kết hoá đơn", "ACC_01", "ACC_01",
                AdvanceVoucherStatus.ChoGiaoTien, null, null, null, null, now, now
        );

        when(advanceVoucherService.createAdvanceVoucher(any(CreateAdvanceVoucherRequest.class))).thenReturn(response);

        String json = """
                {
                    "taskId": "TSK_01",
                    "recipientMemberId": "MEM_01",
                    "advanceAmount": 5000000.00,
                    "receivedDate": "2026-04-01T09:00:00Z",
                    "settlementDeadline": "2026-04-15T17:00:00Z",
                    "commitmentNotes": "Cam kết hoá đơn"
                }
                """;

        mockMvc.perform(post("/api/v1/advances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.advanceVoucherId").value("ADV001"))
                .andExpect(jsonPath("$.data.status").value("ChoGiaoTien"))
                .andExpect(jsonPath("$.data.advanceAmount").value(5000000.00))
                .andExpect(jsonPath("$.data.recipientMemberId").value("MEM_01"));
    }

    @Test
    @DisplayName("POST /api/advances: Route alias cũng lập phiếu tạm ứng thành công (201 Created)")
    void createAdvance_aliasRoute_returns201() throws Exception {
        Instant now = Instant.parse("2026-04-01T09:00:00Z");
        Instant deadline = Instant.parse("2026-04-15T17:00:00Z");

        AdvanceVoucherResponse response = new AdvanceVoucherResponse(
                "ADV002", "TSK_01", "Đặt cỗ", "EVT_01", "Lễ Giỗ", "CLN_01",
                "MEM_01", "Nguyễn Văn A", new BigDecimal("3000000.00"),
                now, deadline, "Cam kết", "ACC_01", "ACC_01",
                AdvanceVoucherStatus.ChoGiaoTien, null, null, null, null, now, now
        );

        when(advanceVoucherService.createAdvanceVoucher(any(CreateAdvanceVoucherRequest.class))).thenReturn(response);

        String json = """
                {
                    "taskId": "TSK_01",
                    "recipientMemberId": "MEM_01",
                    "advanceAmount": 3000000.00,
                    "receivedDate": "2026-04-01T09:00:00Z",
                    "settlementDeadline": "2026-04-15T17:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/advances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.advanceVoucherId").value("ADV002"));
    }

    @Test
    @DisplayName("POST /api/v1/advances: Trả về 400 Bad Request khi thiếu trường bắt buộc")
    void createAdvance_missingRequiredFields_returns400() throws Exception {
        String invalidJson = """
                {
                    "advanceAmount": -100
                }
                """;

        mockMvc.perform(post("/api/v1/advances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/advances/{id}/disburse: Thủ quỹ xác nhận giao tiền trả về 200 OK và trạng thái ChuaTatToan")
    void confirmDisbursement_validRequest_returns200() throws Exception {
        Instant now = Instant.parse("2026-04-01T10:00:00Z");
        AdvanceVoucherResponse response = new AdvanceVoucherResponse(
                "ADV001", "TSK_01", "Đặt cỗ", "EVT_01", "Lễ Giỗ", "CLN_01",
                "MEM_01", "Nguyễn Văn A", new BigDecimal("5000000.00"),
                now, now.plusSeconds(86400), "Cam kết hoá đơn", "ACC_01", "ACC_01",
                AdvanceVoucherStatus.ChuaTatToan, "ACC_TREASURER", "ACC_TREASURER", now, "Đã giao tiền", now, now
        );

        when(advanceVoucherService.confirmDisbursement(eq("ADV001"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/advances/ADV001/disburse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Đã giao tiền\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ChuaTatToan"))
                .andExpect(jsonPath("$.data.treasurerAccountId").value("ACC_TREASURER"));
    }

    @Test
    @DisplayName("POST /api/advances/{id}/confirm: Route alias xác nhận giao tiền trả về 200 OK")
    void confirmDisbursement_aliasRoute_returns200() throws Exception {
        Instant now = Instant.parse("2026-04-01T10:00:00Z");
        AdvanceVoucherResponse response = new AdvanceVoucherResponse(
                "ADV001", "TSK_01", "Đặt cỗ", "EVT_01", "Lễ Giỗ", "CLN_01",
                "MEM_01", "Nguyễn Văn A", new BigDecimal("5000000.00"),
                now, now.plusSeconds(86400), "Cam kết hoá đơn", "ACC_01", "ACC_01",
                AdvanceVoucherStatus.ChuaTatToan, "ACC_TREASURER", "ACC_TREASURER", now, null, now, now
        );

        when(advanceVoucherService.confirmDisbursement(eq("ADV001"), any())).thenReturn(response);

        mockMvc.perform(post("/api/advances/ADV001/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ChuaTatToan"));
    }

    @Test
    @DisplayName("GET /api/v1/advances/{id}: Lấy chi tiết phiếu tạm ứng thành công")
    void getAdvanceVoucherById_returns200() throws Exception {
        Instant now = Instant.parse("2026-04-01T10:00:00Z");
        AdvanceVoucherResponse response = new AdvanceVoucherResponse(
                "ADV001", "TSK_01", "Đặt cỗ", "EVT_01", "Lễ Giỗ", "CLN_01",
                "MEM_01", "Nguyễn Văn A", new BigDecimal("5000000.00"),
                now, now.plusSeconds(86400), "Cam kết", "ACC_01", "ACC_01",
                AdvanceVoucherStatus.ChoGiaoTien, null, null, null, null, now, now
        );

        when(advanceVoucherService.getAdvanceVoucherById("ADV001")).thenReturn(response);

        mockMvc.perform(get("/api/v1/advances/ADV001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.advanceVoucherId").value("ADV001"));
    }

    @Test
    @DisplayName("GET /api/v1/advances/{id}: Không tìm thấy trả về 404 Not Found")
    void getAdvanceVoucherById_notFound_returns404() throws Exception {
        when(advanceVoucherService.getAdvanceVoucherById("ADV_UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Phiếu tạm ứng", "advanceVoucherId", "ADV_UNKNOWN"));

        mockMvc.perform(get("/api/v1/advances/ADV_UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
