package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.response.ClanAssetResponse;
import com.familyfund.contribution.internal.dto.response.ClanAssetSummaryResponse;
import com.familyfund.contribution.internal.service.ClanAssetService;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClanAssetControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClanAssetService clanAssetService;

    @InjectMocks
    private ClanAssetController clanAssetController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(clanAssetController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/clan-assets - 200 OK")
    void searchAssets_success() throws Exception {
        ClanAssetResponse asset = new ClanAssetResponse(
                "AST_001",
                "Đỉnh đồng",
                1,
                "chiếc",
                new BigDecimal("10000000"),
                "Gian chính điện",
                "CTB_001",
                "CLAN_01",
                Instant.now()
        );

        ClanAssetSummaryResponse summary = new ClanAssetSummaryResponse(
                List.of(asset),
                1L,
                1,
                0,
                20,
                1L,
                new BigDecimal("10000000")
        );

        when(clanAssetService.searchAssets(eq("Đỉnh"), any())).thenReturn(summary);

        mockMvc.perform(get("/api/v1/clan-assets?keyword=Đỉnh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].assetName").value("Đỉnh đồng"));
    }

    @Test
    @DisplayName("GET /api/v1/clan-assets/{assetId} - 200 OK")
    void getAssetById_success() throws Exception {
        ClanAssetResponse asset = new ClanAssetResponse(
                "AST_001",
                "Đỉnh đồng",
                1,
                "chiếc",
                new BigDecimal("10000000"),
                "Gian chính điện",
                "CTB_001",
                "CLAN_01",
                Instant.now()
        );

        when(clanAssetService.getAssetById("AST_001")).thenReturn(asset);

        mockMvc.perform(get("/api/v1/clan-assets/AST_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId").value("AST_001"))
                .andExpect(jsonPath("$.data.assetName").value("Đỉnh đồng"));
    }
}
