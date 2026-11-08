package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.api.dto.ContributionType;
import com.familyfund.contribution.api.dto.PaymentMethod;
import com.familyfund.contribution.internal.dto.response.MemberContributionHistoryItemResponse;
import com.familyfund.contribution.internal.dto.response.MemberContributionHistoryResponse;
import com.familyfund.contribution.internal.service.ContributionHistoryService;
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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ContributionHistoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContributionHistoryService contributionHistoryService;

    @InjectMocks
    private ContributionHistoryController contributionHistoryController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(contributionHistoryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/contributions/my-history - 200 OK")
    void getMyContributionHistory_success() throws Exception {
        MemberContributionHistoryItemResponse item = new MemberContributionHistoryItemResponse(
                "CTB01", "PT-20261109-00001", "EVT01", "Lễ Giỗ Tổ",
                "MEM01", "Nguyễn Văn A", ContributionType.Cash, PaymentMethod.Cash,
                null, null, null, new BigDecimal("1000000.00"), new BigDecimal("1000000.00"),
                "Completed", Instant.now()
        );

        MemberContributionHistoryResponse response = new MemberContributionHistoryResponse(
                "MEM01", "Nguyễn Văn A", "BR01", "Chi 1", "CLAN01",
                new BigDecimal("1000000.00"), new BigDecimal("1000000.00"), List.of(item)
        );

        when(contributionHistoryService.getMyContributionHistory(anyBoolean())).thenReturn(response);

        mockMvc.perform(get("/api/v1/contributions/my-history")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value("MEM01"))
                .andExpect(jsonPath("$.data.items[0].receiptNumber").value("PT-20261109-00001"));
    }

    @Test
    @DisplayName("GET /api/v1/contributions/receipts/{receiptNumber}/pdf - 200 OK with PDF media type")
    void downloadReceiptPdf_success() throws Exception {
        String receiptNumber = "PT-20261109-00001";
        byte[] pdfContent = "%PDF-1.4 sample content".getBytes();

        when(contributionHistoryService.downloadReceiptPdf(receiptNumber)).thenReturn(pdfContent);

        mockMvc.perform(get("/api/v1/contributions/receipts/{receiptNumber}/pdf", receiptNumber))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "form-data; name=\"inline\"; filename=\"receipt_PT-20261109-00001.pdf\""))
                .andExpect(content().bytes(pdfContent));
    }
}
