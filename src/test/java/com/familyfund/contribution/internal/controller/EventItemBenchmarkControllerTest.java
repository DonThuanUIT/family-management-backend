package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.request.CreateEventItemBenchmarkRequest;
import com.familyfund.contribution.internal.dto.response.EventItemBenchmarkResponse;
import com.familyfund.contribution.internal.service.EventItemBenchmarkService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EventItemBenchmarkControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EventItemBenchmarkService benchmarkService;

    @InjectMocks
    private EventItemBenchmarkController benchmarkController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(benchmarkController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/benchmarks - 201 Created on valid benchmark")
    void createBenchmark_success() throws Exception {
        EventItemBenchmarkResponse response = new EventItemBenchmarkResponse(
                "BMK_001",
                "EVT_001",
                "CLAN_01",
                "Heo quay",
                "con",
                new BigDecimal("3500000"),
                "Heo quay 10-12kg",
                Instant.now()
        );

        when(benchmarkService.createBenchmark(eq("EVT_001"), any())).thenReturn(response);

        String jsonPayload = """
                {
                    "itemName": "Heo quay",
                    "unit": "con",
                    "unitPrice": 3500000,
                    "note": "Heo quay 10-12kg"
                }
                """;

        mockMvc.perform(post("/api/v1/events/EVT_001/benchmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.benchmarkId").value("BMK_001"))
                .andExpect(jsonPath("$.data.itemName").value("Heo quay"));
    }

    @Test
    @DisplayName("GET /api/v1/events/{eventId}/benchmarks - 200 OK")
    void getBenchmarksByEvent_success() throws Exception {
        EventItemBenchmarkResponse response = new EventItemBenchmarkResponse(
                "BMK_001",
                "EVT_001",
                "CLAN_01",
                "Heo quay",
                "con",
                new BigDecimal("3500000"),
                null,
                Instant.now()
        );

        when(benchmarkService.getBenchmarksByEvent("EVT_001")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/events/EVT_001/benchmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].itemName").value("Heo quay"));
    }

    @Test
    @DisplayName("DELETE /api/v1/events/{eventId}/benchmarks/{benchmarkId} - 200 OK")
    void deleteBenchmark_success() throws Exception {
        mockMvc.perform(delete("/api/v1/events/EVT_001/benchmarks/BMK_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
