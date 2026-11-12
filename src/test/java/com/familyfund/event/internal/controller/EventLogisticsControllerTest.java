package com.familyfund.event.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.event.api.dto.RegistrationStatus;
import com.familyfund.event.internal.dto.response.EventLogisticsSummaryResponse;
import com.familyfund.event.internal.service.EventLogisticsExportService;
import com.familyfund.event.internal.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-42 EventLogisticsController MockMvc Tests")
class EventLogisticsControllerTest {

    private static final String EVENT_ID = "EVT_42";

    @Mock
    private RegistrationService registrationService;

    @Mock
    private EventLogisticsExportService exportService;

    @InjectMocks
    private EventLogisticsController controller;

    private MockMvc mockMvc;

    private EventLogisticsSummaryResponse sampleSummary;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleSummary = new EventLogisticsSummaryResponse(
                EVENT_ID,
                "Giỗ Tổ 2026",
                "BR_01",
                "Chi Nhất",
                2,
                8,
                6,
                2,
                4,
                List.of(new EventLogisticsSummaryResponse.BranchOption("BR_01", "Chi Nhất")),
                List.of(new EventLogisticsSummaryResponse.HouseholdLogisticsItem(
                        "REG_01", "MEM_01", "Nguyễn Văn A", "BR_01", "Chi Nhất",
                        4, 3, 1, 2, "Không", "Xe 2 chỗ",
                        RegistrationStatus.Confirmed, Instant.parse("2026-10-01T08:00:00Z")
                ))
        );
    }

    @Test
    @DisplayName("GET /api/events/{id}/registration-summary returns real-time totals with branchId filter")
    void getLogisticsSummary_apiAlias_success() throws Exception {
        when(registrationService.getEventLogisticsSummary(EVENT_ID, "BR_01")).thenReturn(sampleSummary);

        mockMvc.perform(get("/api/events/{eventId}/registration-summary", EVENT_ID)
                        .param("branchId", "BR_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRegistrations").value(2))
                .andExpect(jsonPath("$.data.totalAttendees").value(8))
                .andExpect(jsonPath("$.data.totalSavoryMeals").value(6))
                .andExpect(jsonPath("$.data.totalVegetarianMeals").value(2))
                .andExpect(jsonPath("$.data.totalShuttleSeats").value(4))
                .andExpect(jsonPath("$.data.branchId").value("BR_01"));

        verify(registrationService).getEventLogisticsSummary(EVENT_ID, "BR_01");
    }

    @Test
    @DisplayName("GET /api/v1/events/{id}/registration-summary returns 404 when event/branch not found")
    void getLogisticsSummary_notFound() throws Exception {
        when(registrationService.getEventLogisticsSummary(EVENT_ID, "UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy chi/nhánh"));

        mockMvc.perform(get("/api/v1/events/{eventId}/registration-summary", EVENT_ID)
                        .param("branchId", "UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/events/{id}/registration-summary/export downloads xlsx attachment")
    void exportLogisticsSummary_xlsx() throws Exception {
        byte[] dummyBytes = new byte[]{1, 2, 3};
        when(registrationService.getEventLogisticsSummary(EVENT_ID, null)).thenReturn(sampleSummary);
        when(exportService.exportLogisticsReport(sampleSummary, "xlsx")).thenReturn(dummyBytes);

        mockMvc.perform(get("/api/events/{eventId}/registration-summary/export", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("danh-sach-diem-danh-chia-co-EVT_42.xlsx")))
                .andExpect(content().bytes(dummyBytes));
    }
}
