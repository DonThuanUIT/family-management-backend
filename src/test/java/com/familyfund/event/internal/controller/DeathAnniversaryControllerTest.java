package com.familyfund.event.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.event.api.DeathAnniversaryService;
import com.familyfund.event.api.dto.DeathAnniversaryDto;
import com.familyfund.event.api.dto.DeathAnniversaryReminderDto;
import com.familyfund.event.api.dto.EventSuggestionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests of {@link DeathAnniversaryController} (US-40): the read endpoints and the manual scan trigger.
 */
@ExtendWith(MockitoExtension.class)
class DeathAnniversaryControllerTest {

    private static final String PATH = "/api/v1/events/death-anniversaries";
    private static final String MEMBER_ID = "MEM_GIO_01";

    private MockMvc mockMvc;

    @Mock
    private DeathAnniversaryService deathAnniversaryService;

    @InjectMocks
    private DeathAnniversaryController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private DeathAnniversaryDto anniversary() {
        return DeathAnniversaryDto.builder()
                .memberId(MEMBER_ID)
                .memberFullName("Nguyễn Văn An")
                .generation(3)
                .branchId("BR_1")
                .branchName("Chi Một")
                .deathDate(LocalDate.of(2020, 3, 8))
                .deathDateLunar("15/02 Giáp Thìn")
                .lunarDay(15).lunarMonth(2).leapMonth(false)
                .anniversaryYear(2026).anniversaryDate(LocalDate.of(2026, 4, 3))
                .daysRemaining(14).milestoneDays(14)
                .reminderTriggered(true).alreadyReminded(true)
                .suggestion(EventSuggestionDto.builder()
                        .suggestionId("GSU_0001").memberId(MEMBER_ID)
                        .eventName("Đám giỗ Nguyễn Văn An (15/02 Bính Ngọ)")
                        .eventDatetime(Instant.parse("2026-04-03T01:00:00Z"))
                        .eventDateLunar("15/02 Bính Ngọ").location("Chi Một")
                        .estimatedBudget(new BigDecimal("5000000"))
                        .description("Đám giỗ Nguyễn Văn An")
                        .daysRemaining(14)
                        .requestBody(EventSuggestionDto.CreateEventSuggestion.builder()
                                .eventName("Đám giỗ Nguyễn Văn An (15/02 Bính Ngọ)")
                                .eventDatetime(Instant.parse("2026-04-03T01:00:00Z"))
                                .eventDateLunar("15/02 Bính Ngọ").location("Chi Một")
                                .estimatedBudget(new BigDecimal("5000000"))
                                .description("Đám giỗ Nguyễn Văn An")
                                .build())
                        .build())
                .computedAt(Instant.parse("2026-03-20T00:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/events/death-anniversaries - 200 with the suggestion payload")
    void upcoming_ReturnsList() throws Exception {
        when(deathAnniversaryService.upcomingAnniversaries(isNull(), eq(0))).thenReturn(List.of(anniversary()));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].memberId").value(MEMBER_ID))
                .andExpect(jsonPath("$.data[0].memberFullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.data[0].daysRemaining").value(14))
                .andExpect(jsonPath("$.data[0].milestoneDays").value(14))
                .andExpect(jsonPath("$.data[0].alreadyReminded").value(true))
                .andExpect(jsonPath("$.data[0].suggestion.eventName").value("Đám giỗ Nguyễn Văn An (15/02 Bính Ngọ)"))
                .andExpect(jsonPath("$.data[0].suggestion.requestBody.eventName")
                        .value("Đám giỗ Nguyễn Văn An (15/02 Bính Ngọ)"))
                .andExpect(jsonPath("$.data[0].suggestion.requestBody.location").value("Chi Một"));
    }

    @Test
    @DisplayName("GET /api/v1/events/death-anniversaries?from=...&days=7 - 200 passes the window through")
    void upcoming_PassesWindow() throws Exception {
        when(deathAnniversaryService.upcomingAnniversaries(LocalDate.of(2026, 3, 20), 7))
                .thenReturn(List.of());

        mockMvc.perform(get(PATH).param("from", "2026-03-20").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(deathAnniversaryService).upcomingAnniversaries(LocalDate.of(2026, 3, 20), 7);
    }

    @Test
    @DisplayName("GET /api/v1/events/death-anniversaries?from=20-03-2026 - 400 for a malformed date")
    void upcoming_RejectsMalformedDate() throws Exception {
        mockMvc.perform(get(PATH).param("from", "20-03-2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/events/death-anniversaries/{memberId}/reminders - 200 with the history")
    void reminders_ReturnsHistory() throws Exception {
        when(deathAnniversaryService.remindersOfMember(MEMBER_ID)).thenReturn(List.of(
                new DeathAnniversaryReminderDto("GIO_1", MEMBER_ID, 2026, 2026, 14,
                        LocalDate.of(2026, 4, 3), LocalDate.of(2026, 3, 20), 2)));

        mockMvc.perform(get(PATH + "/{memberId}/reminders", MEMBER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reminderId").value("GIO_1"))
                .andExpect(jsonPath("$.data[0].milestoneDays").value(14))
                .andExpect(jsonPath("$.data[0].recipientCount").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/events/death-anniversaries/{memberId}/reminders - 404 for an unknown member")
    void reminders_NotFound() throws Exception {
        when(deathAnniversaryService.remindersOfMember("MEM_MISSING"))
                .thenThrow(new ResourceNotFoundException("Thành viên", "memberId", "MEM_MISSING"));

        mockMvc.perform(get(PATH + "/{memberId}/reminders", "MEM_MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/events/death-anniversaries/scan - 200 with the number of reminders sent")
    void scan_ReturnsCount() throws Exception {
        when(deathAnniversaryService.scanAndSendReminders(null)).thenReturn(3);

        mockMvc.perform(post(PATH + "/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remindersSent").value(3))
                .andExpect(jsonPath("$.data.referenceDate").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/events/death-anniversaries/scan?date=... - 200 replays a past day")
    void scan_WithExplicitDate() throws Exception {
        when(deathAnniversaryService.scanAndSendReminders(LocalDate.of(2026, 3, 20))).thenReturn(1);

        mockMvc.perform(post(PATH + "/scan").param("date", "2026-03-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remindersSent").value(1))
                .andExpect(jsonPath("$.data.referenceDate").value("2026-03-20"));
    }

    @Test
    @DisplayName("POST /api/v1/events/death-anniversaries/scan - 400 for a malformed date")
    void scan_RejectsMalformedDate() throws Exception {
        mockMvc.perform(post(PATH + "/scan").param("date", "khong-phai-ngay"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the service is asked for a zero-day window when the client omits days")
    void upcoming_DefaultWindow() throws Exception {
        when(deathAnniversaryService.upcomingAnniversaries(any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get(PATH)).andExpect(status().isOk());

        verify(deathAnniversaryService).upcomingAnniversaries(isNull(), eq(0));
    }
}
