package com.familyfund.event.internal.controller;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.internal.dto.request.AssignPersonnelRequest;
import com.familyfund.event.internal.dto.request.CreateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventStatusRequest;
import com.familyfund.event.internal.dto.response.EventDetailResponse;
import com.familyfund.event.internal.dto.response.EventNotificationResponse;
import com.familyfund.event.internal.dto.response.EventPersonnelResponse;
import com.familyfund.event.internal.dto.response.EventResponse;
import com.familyfund.event.internal.service.EventService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    private static final String EVENT_ID = "EVT_TEST_123";

    private MockMvc mockMvc;

    @Mock
    private EventService eventService;

    @InjectMocks
    private EventController eventController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(eventController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/events - 201 Created on valid payload")
    void createEventSuccess() throws Exception {
        EventResponse response = new EventResponse(
                EVENT_ID, "CLAN_1", "Giỗ Tổ 2026", Instant.parse("2026-04-26T08:00:00Z"),
                "10/03 Bính Ngọ", "Từ đường họ Nguyễn", BigDecimal.valueOf(50_000_000),
                "Mô tả", EventStatus.Draft, "ACC_1", null);

        when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(response);

        String payload = """
                {
                    "eventName": "Giỗ Tổ 2026",
                    "eventDatetime": "2026-04-26T08:00:00Z",
                    "eventDateLunar": "10/03 Bính Ngọ",
                    "location": "Từ đường họ Nguyễn",
                    "estimatedBudget": 50000000.00,
                    "description": "Mô tả"
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value(EVENT_ID))
                .andExpect(jsonPath("$.data.eventName").value("Giỗ Tổ 2026"))
                .andExpect(jsonPath("$.data.status").value("Draft"));

        verify(eventService).createEvent(any(CreateEventRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/events - 400 Bad Request when required fields are missing")
    void createEventMissingFields() throws Exception {
        String payload = """
                {
                    "eventName": "",
                    "location": ""
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/events/{id} - 200 OK on valid update payload")
    void updateEventSuccess() throws Exception {
        EventResponse response = new EventResponse(
                EVENT_ID, "CLAN_1", "Giỗ Tổ 2026 (Mới)", Instant.parse("2026-04-27T08:00:00Z"),
                "11/03 Bính Ngọ", "Từ đường Đại Tộc", BigDecimal.valueOf(60_000_000),
                "Cập nhật", EventStatus.Draft, "ACC_1", null);

        when(eventService.updateEvent(eq(EVENT_ID), any(UpdateEventRequest.class))).thenReturn(response);

        String payload = """
                {
                    "eventName": "Giỗ Tổ 2026 (Mới)",
                    "eventDatetime": "2026-04-27T08:00:00Z",
                    "eventDateLunar": "11/03 Bính Ngọ",
                    "location": "Từ đường Đại Tộc",
                    "estimatedBudget": 60000000.00,
                    "description": "Cập nhật"
                }
                """;

        mockMvc.perform(put("/api/v1/events/{id}", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventName").value("Giỗ Tổ 2026 (Mới)"))
                .andExpect(jsonPath("$.data.location").value("Từ đường Đại Tộc"));

        verify(eventService).updateEvent(eq(EVENT_ID), any(UpdateEventRequest.class));
    }

    @Test
    @DisplayName("PUT /api/v1/events/{id} - 400 Bad Request on invalid update payload")
    void updateEventInvalidPayload() throws Exception {
        String payload = """
                {
                    "eventName": "",
                    "location": ""
                }
                """;

        mockMvc.perform(put("/api/v1/events/{id}", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/events - 200 OK with list of events")
    void listEvents() throws Exception {
        EventResponse response = new EventResponse(
                EVENT_ID, "CLAN_1", "Giỗ Tổ", Instant.parse("2026-04-26T08:00:00Z"),
                null, "Từ đường", BigDecimal.ZERO, null, EventStatus.Draft, "ACC_1", null);
        when(eventService.listEvents()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].eventId").value(EVENT_ID));
    }

    @Test
    @DisplayName("GET /api/v1/events/{id} - 200 OK with detail")
    void getEventDetailSuccess() throws Exception {
        EventDetailResponse response = new EventDetailResponse(
                EVENT_ID, "CLAN_1", "Giỗ Tổ", Instant.parse("2026-04-26T08:00:00Z"),
                null, "Từ đường", BigDecimal.ZERO, null, EventStatus.Draft, "ACC_1", null, List.of());
        when(eventService.getEventDetail(EVENT_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/events/{id}", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value(EVENT_ID));
    }

    @Test
    @DisplayName("GET /api/v1/events/{id} - 404 Not Found when event missing")
    void getEventDetailNotFound() throws Exception {
        when(eventService.getEventDetail(EVENT_ID))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy sự kiện"));

        mockMvc.perform(get("/api/v1/events/{id}", EVENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/events/{id}/status - 200 OK on valid status update")
    void updateEventStatusSuccess() throws Exception {
        EventResponse response = new EventResponse(
                EVENT_ID, "CLAN_1", "Giỗ Tổ", Instant.parse("2026-04-26T08:00:00Z"),
                null, "Từ đường", BigDecimal.ZERO, null, EventStatus.RegistrationOpen, "ACC_1", null);
        when(eventService.updateEventStatus(eq(EVENT_ID), any(UpdateEventStatusRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/events/{id}/status", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RegistrationOpen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RegistrationOpen"));
    }

    @Test
    @DisplayName("PATCH /api/v1/events/{id}/status - 400 Bad Request on illegal transition")
    void updateEventStatusIllegalTransition() throws Exception {
        when(eventService.updateEventStatus(eq(EVENT_ID), any(UpdateEventStatusRequest.class)))
                .thenThrow(new BusinessRuleException("Không thể chuyển đổi trạng thái"));

        mockMvc.perform(patch("/api/v1/events/{id}/status", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"Ongoing\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/events/{id}/personnel - 200 OK on valid personnel assignment")
    void assignPersonnelSuccess() throws Exception {
        EventPersonnelResponse p = new EventPersonnelResponse(
                EVENT_ID, "MEM_1", "Nguyễn Văn A", EventRole.HeadOfOrganizer, false, Instant.now());
        when(eventService.assignPersonnel(eq(EVENT_ID), any(AssignPersonnelRequest.class)))
                .thenReturn(List.of(p));

        String payload = """
                {
                    "personnel": [
                        {
                            "memberId": "MEM_1",
                            "role": "HeadOfOrganizer"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/events/{id}/personnel", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].memberId").value("MEM_1"))
                .andExpect(jsonPath("$.data[0].eventRole").value("HeadOfOrganizer"));
    }

    @Test
    @DisplayName("POST /api/v1/events/{id}/personnel - 400 Bad Request on empty personnel list")
    void assignPersonnelEmptyList() throws Exception {
        mockMvc.perform(post("/api/v1/events/{id}/personnel", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personnel\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/events/{id}/notifications - 200 OK with notifications list")
    void getEventNotificationsSuccess() throws Exception {
        EventNotificationResponse notif = new EventNotificationResponse(
                "NTF_001", EVENT_ID, "Thông báo mở đăng ký", "Nội dung mở đăng ký", Instant.now(), "ACC_1"
        );
        when(eventService.getEventNotifications(EVENT_ID)).thenReturn(List.of(notif));

        mockMvc.perform(get("/api/v1/events/{id}/notifications", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].notificationId").value("NTF_001"))
                .andExpect(jsonPath("$.data[0].title").value("Thông báo mở đăng ký"));

        verify(eventService).getEventNotifications(EVENT_ID);
    }
}
