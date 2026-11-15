package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.api.dto.ClanBankingInfoDto;
import com.familyfund.genealogy.internal.dto.request.UpdateClanBankingRequest;
import com.familyfund.genealogy.internal.service.ClanService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClanControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClanService clanService;

    @InjectMocks
    private ClanController clanController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(clanController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/clans/banking-info returns clan banking details")
    void getClanBankingInfo_success() throws Exception {
        ClanBankingInfoDto dto = new ClanBankingInfoDto(
                "CLAN_01",
                "Họ Nguyễn",
                "970422",
                "MBBank",
                "0987654321",
                "NGUYEN VAN THU QUY",
                "compact2"
        );
        when(clanService.getCurrentClanBankingInfo()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/clans/banking-info")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clanId").value("CLAN_01"))
                .andExpect(jsonPath("$.data.bankBin").value("970422"))
                .andExpect(jsonPath("$.data.bankName").value("MBBank"))
                .andExpect(jsonPath("$.data.bankAccountNumber").value("0987654321"))
                .andExpect(jsonPath("$.data.bankAccountName").value("NGUYEN VAN THU QUY"));
    }

    @Test
    @DisplayName("PUT /api/v1/clans/banking-info updates and returns new banking info")
    void updateClanBankingInfo_success() throws Exception {
        ClanBankingInfoDto updated = new ClanBankingInfoDto(
                "CLAN_01",
                "Họ Nguyễn",
                "970436",
                "Vietcombank",
                "1023456789",
                "NGUYEN VAN A",
                "compact"
        );
        when(clanService.updateCurrentClanBankingInfo(any(UpdateClanBankingRequest.class)))
                .thenReturn(updated);

        String payload = """
                {
                    "bankBin": "970436",
                    "bankAccountNumber": "1023456789",
                    "bankAccountName": "NGUYEN VAN A",
                    "qrTemplate": "compact"
                }
                """;

        mockMvc.perform(put("/api/v1/clans/banking-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankBin").value("970436"))
                .andExpect(jsonPath("$.data.bankName").value("Vietcombank"))
                .andExpect(jsonPath("$.data.bankAccountNumber").value("1023456789"))
                .andExpect(jsonPath("$.message").value("Cập nhật thông tin tài khoản ngân hàng dòng họ thành công"));
    }

    @Test
    @DisplayName("PUT /api/v1/clans/banking-info with invalid BIN returns 400 Bad Request")
    void updateClanBankingInfo_invalidBin_returns400() throws Exception {
        String invalidPayload = """
                {
                    "bankBin": "123",
                    "bankAccountNumber": "1023456789",
                    "bankAccountName": "NGUYEN VAN A"
                }
                """;

        mockMvc.perform(put("/api/v1/clans/banking-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }
}
