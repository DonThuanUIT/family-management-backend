package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.api.dto.PaymentMethod;
import com.familyfund.contribution.internal.dto.request.MemberAllocationRequest;
import com.familyfund.contribution.internal.dto.request.RecordCashContributionRequest;
import com.familyfund.contribution.internal.dto.response.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.MemberAllocationResponse;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CashContributionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CashContributionService cashContributionService;

    @InjectMocks
    private CashContributionController cashContributionController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(cashContributionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/contributions/cash - 201 Created on valid single payer payload")
    void recordCashContribution_singlePayer_success() throws Exception {
        ElectronicReceiptResponse response = new ElectronicReceiptResponse(
                "PT-20261029-A1B2C3",
                "CTB_001",
                "EVT_001",
                "Lễ Giỗ Tổ",
                "MEM_001",
                "Nguyễn Văn A",
                "ACC_001",
                BigDecimal.valueOf(1000000),
                "Một triệu đồng chẵn",
                PaymentMethod.Cash,
                Instant.parse("2026-10-29T10:00:00Z"),
                List.of(new MemberAllocationResponse("MEM_001", "Nguyễn Văn A", BigDecimal.valueOf(1000000)))
        );

        when(cashContributionService.recordCashContribution(any(RecordCashContributionRequest.class)))
                .thenReturn(response);

        String json = """
                {
                    "eventId": "EVT_001",
                    "payerMemberId": "MEM_001",
                    "amount": 1000000,
                    "paymentMethod": "Cash"
                }
                """;

        mockMvc.perform(post("/api/v1/contributions/cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cash contribution recorded and electronic receipt issued successfully"))
                .andExpect(jsonPath("$.data.receiptNumber").value("PT-20261029-A1B2C3"))
                .andExpect(jsonPath("$.data.amount").value(1000000))
                .andExpect(jsonPath("$.data.amountInWords").value("Một triệu đồng chẵn"))
                .andExpect(jsonPath("$.data.paymentMethod").value("Cash"))
                .andExpect(jsonPath("$.data.allocations[0].memberId").value("MEM_001"));
    }

    @Test
    @DisplayName("POST /api/v1/contributions/cash - 201 Created on joint contribution with allocations")
    void recordCashContribution_joint_success() throws Exception {
        ElectronicReceiptResponse response = new ElectronicReceiptResponse(
                "PT-20261029-X9Y8Z7",
                "CTB_002",
                "EVT_001",
                "Lễ Giỗ Tổ",
                "MEM_001",
                "Nguyễn Văn Cha",
                "ACC_001",
                BigDecimal.valueOf(1500000),
                "Một triệu năm trăm nghìn đồng chẵn",
                PaymentMethod.BankTransfer,
                Instant.parse("2026-10-29T10:30:00Z"),
                List.of(
                        new MemberAllocationResponse("MEM_CON_1", "Nguyễn Con Một", BigDecimal.valueOf(500000)),
                        new MemberAllocationResponse("MEM_CON_2", "Nguyễn Con Hai", BigDecimal.valueOf(1000000))
                )
        );

        when(cashContributionService.recordCashContribution(any(RecordCashContributionRequest.class)))
                .thenReturn(response);

        String json = """
                {
                    "eventId": "EVT_001",
                    "payerMemberId": "MEM_001",
                    "amount": 1500000,
                    "paymentMethod": "BankTransfer",
                    "allocations": [
                        { "memberId": "MEM_CON_1", "amount": 500000 },
                        { "memberId": "MEM_CON_2", "amount": 1000000 }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/contributions/cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.receiptNumber").value("PT-20261029-X9Y8Z7"))
                .andExpect(jsonPath("$.data.amount").value(1500000))
                .andExpect(jsonPath("$.data.paymentMethod").value("BankTransfer"))
                .andExpect(jsonPath("$.data.allocations").isArray())
                .andExpect(jsonPath("$.data.allocations.length()").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/contributions/cash - 400 Bad Request on missing mandatory fields")
    void recordCashContribution_validationError() throws Exception {
        String invalidJson = """
                {
                    "eventId": "",
                    "payerMemberId": "",
                    "amount": -50000,
                    "paymentMethod": null
                }
                """;

        mockMvc.perform(post("/api/v1/contributions/cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/contributions/cash - 400 Bad Request on BusinessRuleException (event closed)")
    void recordCashContribution_businessRuleViolation() throws Exception {
        when(cashContributionService.recordCashContribution(any(RecordCashContributionRequest.class)))
                .thenThrow(new BusinessRuleException("Cannot record contribution for a closed event"));

        String json = """
                {
                    "eventId": "EVT_CLOSED",
                    "payerMemberId": "MEM_001",
                    "amount": 500000,
                    "paymentMethod": "Cash"
                }
                """;

        mockMvc.perform(post("/api/v1/contributions/cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot record contribution for a closed event"));
    }

    @Test
    @DisplayName("POST /api/v1/contributions/cash - 404 Not Found on ResourceNotFoundException")
    void recordCashContribution_resourceNotFound() throws Exception {
        when(cashContributionService.recordCashContribution(any(RecordCashContributionRequest.class)))
                .thenThrow(new ResourceNotFoundException("Event not found with ID: EVT_999"));

        String json = """
                {
                    "eventId": "EVT_999",
                    "payerMemberId": "MEM_001",
                    "amount": 500000,
                    "paymentMethod": "Cash"
                }
                """;

        mockMvc.perform(post("/api/v1/contributions/cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Event not found with ID: EVT_999"));
    }

    @Test
    @DisplayName("GET /api/v1/contributions/cash/receipts/{receiptNumber} - 200 OK")
    void getReceiptByReceiptNumber_success() throws Exception {
        ElectronicReceiptResponse response = new ElectronicReceiptResponse(
                "PT-20261029-A1B2C3",
                "CTB_001",
                "EVT_001",
                "Lễ Giỗ Tổ",
                "MEM_001",
                "Nguyễn Văn A",
                "ACC_001",
                BigDecimal.valueOf(1000000),
                "Một triệu đồng chẵn",
                PaymentMethod.Cash,
                Instant.parse("2026-10-29T10:00:00Z"),
                List.of(new MemberAllocationResponse("MEM_001", "Nguyễn Văn A", BigDecimal.valueOf(1000000)))
        );

        when(cashContributionService.getReceiptByReceiptNumber("PT-20261029-A1B2C3"))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/contributions/cash/receipts/PT-20261029-A1B2C3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Electronic receipt retrieved successfully"))
                .andExpect(jsonPath("$.data.receiptNumber").value("PT-20261029-A1B2C3"))
                .andExpect(jsonPath("$.data.payerMemberName").value("Nguyễn Văn A"));
    }
}
