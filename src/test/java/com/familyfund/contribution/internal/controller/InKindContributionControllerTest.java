package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.api.dto.ConversionMethod;
import com.familyfund.contribution.api.dto.InKindProcessingStatus;
import com.familyfund.contribution.internal.dto.response.InKindContributionDetailResponse;
import com.familyfund.contribution.internal.dto.response.InKindElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.MemberAllocationResponse;
import com.familyfund.contribution.internal.service.InKindContributionService;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InKindContributionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InKindContributionService inKindContributionService;

    @InjectMocks
    private InKindContributionController inKindContributionController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(inKindContributionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/contributions/in-kind - 201 Created on valid payload")
    void recordInKindContribution_success() throws Exception {
        InKindElectronicReceiptResponse response = new InKindElectronicReceiptResponse(
                "PT-20261031-123456",
                "CTB_001",
                "EVT_001",
                "Giỗ Tổ 2026",
                "MEM_001",
                "Nguyễn Văn A",
                "ACC_001",
                "Bộ lư đồng tam sự",
                BigDecimal.ONE,
                "bộ",
                ConversionMethod.Agreement,
                new BigDecimal("15000000"),
                "Mười lăm triệu đồng",
                InKindProcessingStatus.Pending,
                Instant.now(),
                List.of(new MemberAllocationResponse("MEM_001", "Nguyễn Văn A", new BigDecimal("15000000")))
        );

        when(inKindContributionService.recordInKindContribution(any())).thenReturn(response);

        String jsonPayload = """
                {
                    "eventId": "EVT_001",
                    "payerMemberId": "MEM_001",
                    "itemName": "Bộ lư đồng tam sự",
                    "quantity": 1.0,
                    "unit": "bộ",
                    "estimatedValue": 15000000,
                    "conversionMethod": "Agreement",
                    "convertedMonetaryValue": 15000000
                }
                """;

        mockMvc.perform(post("/api/v1/contributions/in-kind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.receiptNumber").value("PT-20261031-123456"))
                .andExpect(jsonPath("$.data.itemName").value("Bộ lư đồng tam sự"))
                .andExpect(jsonPath("$.data.convertedMonetaryValue").value(15000000));
    }

    @Test
    @DisplayName("POST /api/v1/contributions/in-kind - 400 Bad Request on missing required fields")
    void recordInKindContribution_missingFields_returns400() throws Exception {
        String invalidPayload = """
                {
                    "eventId": "",
                    "payerMemberId": "MEM_001",
                    "itemName": "",
                    "quantity": 0
                }
                """;

        mockMvc.perform(post("/api/v1/contributions/in-kind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/contributions/in-kind/receipts/{receiptNumber} - 200 OK")
    void getReceiptByReceiptNumber_success() throws Exception {
        InKindElectronicReceiptResponse response = new InKindElectronicReceiptResponse(
                "PT-20261031-123456",
                "CTB_001",
                "EVT_001",
                "Giỗ Tổ 2026",
                "MEM_001",
                "Nguyễn Văn A",
                "ACC_001",
                "Đỉnh đồng",
                BigDecimal.ONE,
                "cái",
                ConversionMethod.Agreement,
                new BigDecimal("10000000"),
                "Mười triệu đồng",
                InKindProcessingStatus.Pending,
                Instant.now(),
                List.of(new MemberAllocationResponse("MEM_001", "Nguyễn Văn A", new BigDecimal("10000000")))
        );

        when(inKindContributionService.getReceiptByReceiptNumber("PT-20261031-123456")).thenReturn(response);

        mockMvc.perform(get("/api/v1/contributions/in-kind/receipts/PT-20261031-123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.receiptNumber").value("PT-20261031-123456"))
                .andExpect(jsonPath("$.data.itemName").value("Đỉnh đồng"));
    }

    @Test
    @DisplayName("GET /api/v1/contributions/in-kind/{contributionId} - 200 OK")
    void getInKindContributionById_success() throws Exception {
        InKindContributionDetailResponse response = new InKindContributionDetailResponse(
                "CTB_001",
                "PT-20261031-123456",
                "EVT_001",
                "Giỗ Tổ 2026",
                "MEM_001",
                "Nguyễn Văn A",
                "ACC_001",
                "Đỉnh đồng",
                BigDecimal.ONE,
                "cái",
                new BigDecimal("10000000"),
                ConversionMethod.Agreement,
                new BigDecimal("10000000"),
                "Mười triệu đồng",
                InKindProcessingStatus.Pending,
                Instant.now(),
                List.of(new MemberAllocationResponse("MEM_001", "Nguyễn Văn A", new BigDecimal("10000000")))
        );

        when(inKindContributionService.getInKindContributionById("CTB_001")).thenReturn(response);

        mockMvc.perform(get("/api/v1/contributions/in-kind/CTB_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contributionId").value("CTB_001"));
    }

    @Test
    @DisplayName("PUT /api/v1/contributions/in-kind/{contributionId}/lifecycle - 200 OK")
    void processLifecycle_success() throws Exception {
        com.familyfund.contribution.internal.dto.response.ProcessInKindLifecycleResponse response =
                new com.familyfund.contribution.internal.dto.response.ProcessInKindLifecycleResponse(
                        "CTB_001",
                        "PT-001",
                        "Lư đồng",
                        InKindProcessingStatus.AddedToCommonAssets,
                        "Nhập kho",
                        new com.familyfund.contribution.internal.dto.response.ClanAssetResponse(
                                "AST_001", "Lư đồng", 1, "cái", new BigDecimal("10000000"),
                                "Gian chính điện", "CTB_001", "CLAN_01", Instant.now()
                        )
                );

        when(inKindContributionService.processLifecycle(eq("CTB_001"), any())).thenReturn(response);

        String jsonPayload = """
                {
                    "targetStatus": "AddedToCommonAssets",
                    "note": "Nhập kho",
                    "storageLocation": "Gian chính điện"
                }
                """;

        mockMvc.perform(put("/api/v1/contributions/in-kind/CTB_001/lifecycle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processingStatus").value("AddedToCommonAssets"))
                .andExpect(jsonPath("$.data.asset.assetId").value("AST_001"));
    }

    @Test
    @DisplayName("GET /api/v1/contributions/in-kind/events/{eventId} - 200 OK")
    void getInKindContributionsByEvent_success() throws Exception {
        InKindContributionDetailResponse response = new InKindContributionDetailResponse(
                "CTB_001",
                "PT-20261031-123456",
                "EVT_001",
                "Giỗ Tổ 2026",
                "MEM_001",
                "Nguyễn Văn A",
                "ACC_001",
                "Đỉnh đồng",
                BigDecimal.ONE,
                "cái",
                new BigDecimal("10000000"),
                ConversionMethod.Agreement,
                new BigDecimal("10000000"),
                "Mười triệu đồng",
                InKindProcessingStatus.Pending,
                Instant.now(),
                List.of(new MemberAllocationResponse("MEM_001", "Nguyễn Văn A", new BigDecimal("10000000")))
        );

        when(inKindContributionService.getInKindContributionsByEvent("EVT_001", null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/contributions/in-kind/events/EVT_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].itemName").value("Đỉnh đồng"));
    }
}
