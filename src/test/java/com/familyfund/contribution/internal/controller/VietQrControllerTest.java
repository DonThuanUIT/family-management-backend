package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.request.GenerateVietQrRequest;
import com.familyfund.contribution.internal.dto.request.VietQrWebhookPayload;
import com.familyfund.contribution.internal.dto.response.VietQrResponse;
import com.familyfund.contribution.internal.dto.response.WebhookPaymentResultResponse;
import com.familyfund.contribution.internal.service.VietQrService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VietQrControllerTest {

    private MockMvc mockMvc;

    @Mock
    private VietQrService vietQrService;

    @InjectMocks
    private VietQrController vietQrController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(vietQrController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/contributions/vietqr/generate - 200 OK")
    void generateVietQr_success() throws Exception {
        VietQrResponse response = new VietQrResponse(
                "https://img.vietqr.io/image/970436-1023456789-compact2.png",
                "vietqr://transfer?bin=970436",
                "970436",
                "Vietcombank",
                "1023456789",
                "QUY DONG HO",
                BigDecimal.valueOf(500000),
                "FFUND EVT01 MEM01",
                "EVT01",
                "Lễ Giỗ Tổ",
                "MEM01",
                "Nguyễn Văn A"
        );

        when(vietQrService.generateVietQr(any(GenerateVietQrRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/contributions/vietqr/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\": \"EVT01\", \"memberId\": \"MEM01\", \"amount\": 500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transferContent").value("FFUND EVT01 MEM01"))
                .andExpect(jsonPath("$.data.qrCodeUrl").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/contributions/vietqr/webhook - 200 OK")
    void receivePaymentWebhook_success() throws Exception {
        WebhookPaymentResultResponse response = new WebhookPaymentResultResponse(
                true,
                "Payment verified, cash contribution recorded",
                "FT26110912345",
                "CTB01",
                "PT-20261109-00001"
        );

        when(vietQrService.processPaymentWebhook(any(VietQrWebhookPayload.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/contributions/vietqr/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"FFUND EVT01 MEM01\", \"transferAmount\": 500000, \"referenceCode\": \"FT26110912345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.referenceCode").value("FT26110912345"))
                .andExpect(jsonPath("$.data.receiptNumber").value("PT-20261109-00001"));
    }
}
