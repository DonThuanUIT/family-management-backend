package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.api.dto.InKindProcessingStatus;
import com.familyfund.contribution.internal.dto.request.ProcessInKindLifecycleRequest;
import com.familyfund.contribution.internal.dto.response.ClanAssetResponse;
import com.familyfund.contribution.internal.dto.response.EventPendingInKindCheckResponse;
import com.familyfund.contribution.internal.dto.response.ProcessInKindLifecycleResponse;
import com.familyfund.contribution.internal.service.InKindContributionService;
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
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InKindPostEventStatusControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InKindContributionService inKindContributionService;

    @InjectMocks
    private InKindContributionController inKindContributionController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(inKindContributionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("PUT /api/v1/contributions/in-kind/{id}/status - 200 OK (Task #180)")
    void processLifecycle_viaStatusUrl_success() throws Exception {
        ProcessInKindLifecycleResponse response = new ProcessInKindLifecycleResponse(
                "CTB_001",
                "PT-20261109-00001",
                "Heo quay",
                InKindProcessingStatus.Consumed,
                "Tiêu hao trong cỗ giỗ",
                null
        );

        when(inKindContributionService.processLifecycle(eq("CTB_001"), any(ProcessInKindLifecycleRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/contributions/in-kind/CTB_001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\": \"Consumed\", \"note\": \"Tiêu hao trong cỗ giỗ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contributionId").value("CTB_001"))
                .andExpect(jsonPath("$.data.processingStatus").value("Consumed"));
    }

    @Test
    @DisplayName("GET /api/v1/contributions/in-kind/events/{id}/pending-check - 200 OK (Task #180)")
    void checkPendingInKindContributions_success() throws Exception {
        EventPendingInKindCheckResponse response = new EventPendingInKindCheckResponse(
                "EVT_001",
                "Đại Lễ Giỗ Tổ",
                false,
                0,
                "Tất cả hiện vật cúng tiến trong sự kiện đã được phân loại vòng đời hoàn tất.",
                Collections.emptyList()
        );

        when(inKindContributionService.checkPendingInKindContributions("EVT_001"))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/contributions/in-kind/events/EVT_001/pending-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value("EVT_001"))
                .andExpect(jsonPath("$.data.hasPendingItems").value(false))
                .andExpect(jsonPath("$.data.pendingCount").value(0));
    }
}
