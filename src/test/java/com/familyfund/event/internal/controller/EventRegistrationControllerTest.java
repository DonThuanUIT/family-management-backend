package com.familyfund.event.internal.controller;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.event.api.dto.RegistrationStatus;
import com.familyfund.event.internal.dto.request.RegisterAttendeesRequest;
import com.familyfund.event.internal.dto.request.RequestCancelRegistrationRequest;
import com.familyfund.event.internal.dto.request.RequestModifyRegistrationRequest;
import com.familyfund.event.internal.dto.request.ReviewRegistrationRequest;
import com.familyfund.event.internal.dto.response.RegistrationMemberDetailResponse;
import com.familyfund.event.internal.dto.response.RegistrationResponse;
import com.familyfund.event.internal.dto.response.RegistrationSummaryResponse;
import com.familyfund.event.internal.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventRegistrationController WebMvc Tests")
class EventRegistrationControllerTest {

    private static final String EVENT_ID = "EVT_TEST_01";
    private static final String REG_ID = "REG_20261101_ABC123";
    private static final String REP_ID = "MEM_REP_01";

    private MockMvc mockMvc;

    @Mock
    private RegistrationService registrationService;

    @InjectMocks
    private EventRegistrationController registrationController;

    private RegistrationResponse sampleResponse;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(registrationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        sampleResponse = new RegistrationResponse(
                REG_ID,
                EVENT_ID,
                REP_ID,
                "Nguyễn Văn Đại Diện",
                4,
                1,
                3,
                "1 chay",
                "Xe ô tô riêng",
                Instant.now(),
                RegistrationStatus.Confirmed,
                null, null, null, null, null, null,
                null, null, null, null,
                List.of(new RegistrationMemberDetailResponse("MEM_002", "Nguyễn Văn Con", false))
        );
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/registrations - 201 Created on valid submission")
    void registerAttendees_success() throws Exception {
        when(registrationService.registerAttendees(eq(EVENT_ID), any(RegisterAttendeesRequest.class)))
                .thenReturn(sampleResponse);

        String payload = """
                {
                    "totalAttendees": 4,
                    "vegetarianMeals": 1,
                    "savoryMeals": 3,
                    "dietaryRequirements": "1 chay",
                    "transportationNotes": "Xe ô tô riêng",
                    "attendingMemberIds": ["MEM_002"]
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/registrations", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng ký tham gia sự kiện thành công"))
                .andExpect(jsonPath("$.data.registrationId").value(REG_ID))
                .andExpect(jsonPath("$.data.representativeMemberName").value("Nguyễn Văn Đại Diện"))
                .andExpect(jsonPath("$.data.totalAttendees").value(4))
                .andExpect(jsonPath("$.data.vegetarianMeals").value(1))
                .andExpect(jsonPath("$.data.savoryMeals").value(3))
                .andExpect(jsonPath("$.data.attendingMembers[0].fullName").value("Nguyễn Văn Con"));

        verify(registrationService).registerAttendees(eq(EVENT_ID), any(RegisterAttendeesRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/registrations - 400 Bad Request on totalAttendees < 1")
    void registerAttendees_validationFailure() throws Exception {
        String payload = """
                {
                    "totalAttendees": 0,
                    "vegetarianMeals": 0,
                    "savoryMeals": 0
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/registrations", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/registrations - 400 Bad Request on BusinessRuleException")
    void registerAttendees_businessRuleFailure() throws Exception {
        when(registrationService.registerAttendees(eq(EVENT_ID), any(RegisterAttendeesRequest.class)))
                .thenThrow(new BusinessRuleException("Sự kiện hiện không ở trạng thái mở tiếp nhận đăng ký"));

        String payload = """
                {
                    "totalAttendees": 2,
                    "vegetarianMeals": 0,
                    "savoryMeals": 2
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/registrations", EVENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Sự kiện hiện không ở trạng thái mở tiếp nhận đăng ký"));
    }

    @Test
    @DisplayName("GET /api/v1/events/{eventId}/registrations/summary - 200 OK with aggregated metrics")
    void getRegistrationSummary_success() throws Exception {
        RegistrationSummaryResponse summary = new RegistrationSummaryResponse(EVENT_ID, 10, 35, 5, 30);
        when(registrationService.getRegistrationSummary(EVENT_ID)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/events/{eventId}/registrations/summary", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value(EVENT_ID))
                .andExpect(jsonPath("$.data.totalRegistrations").value(10))
                .andExpect(jsonPath("$.data.totalAttendees").value(35))
                .andExpect(jsonPath("$.data.totalVegetarianMeals").value(5))
                .andExpect(jsonPath("$.data.totalSavoryMeals").value(30));
    }

    @Test
    @DisplayName("GET /api/v1/events/{eventId}/registrations/summary - 404 Not Found on unknown event")
    void getRegistrationSummary_notFound() throws Exception {
        when(registrationService.getRegistrationSummary("UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy sự kiện"));

        mockMvc.perform(get("/api/v1/events/{eventId}/registrations/summary", "UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/events/{eventId}/registrations - 200 OK with paginated list")
    void listRegistrations_success() throws Exception {
        Pageable pageable = PageRequest.of(0, 10);
        Page<RegistrationResponse> page = new PageImpl<>(List.of(sampleResponse), pageable, 1);
        when(registrationService.listRegistrations(eq(EVENT_ID), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/events/{eventId}/registrations", EVENT_ID)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].registrationId").value(REG_ID));
    }

    @Test
    @DisplayName("GET /api/v1/events/{eventId}/registrations/me - 200 OK with current user registration")
    void getMyRegistration_success() throws Exception {
        when(registrationService.getMyRegistration(EVENT_ID)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/events/{eventId}/registrations/me", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.registrationId").value(REG_ID));
    }

    @Test
    @DisplayName("GET /api/v1/events/{eventId}/registrations/{registrationId} - 200 OK with detail")
    void getRegistrationDetail_success() throws Exception {
        when(registrationService.getRegistrationById(EVENT_ID, REG_ID)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/events/{eventId}/registrations/{registrationId}", EVENT_ID, REG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.registrationId").value(REG_ID));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/registrations/{registrationId}/request-modification - 200 OK")
    void requestModification_success() throws Exception {
        RegistrationResponse pendingModResponse = new RegistrationResponse(
                REG_ID, EVENT_ID, REP_ID, "Nguyễn Văn Đại Diện",
                4, 1, 3, "1 chay", "Xe ô tô", Instant.now(),
                RegistrationStatus.PendingModification,
                2, 0, 2, "2 mặn", null, "Hai cháu bận thi học kỳ",
                null, null, null, null, List.of()
        );

        when(registrationService.requestModification(eq(EVENT_ID), eq(REG_ID), any(RequestModifyRegistrationRequest.class)))
                .thenReturn(pendingModResponse);

        String payload = """
                {
                    "proposedTotalAttendees": 2,
                    "proposedVegetarianMeals": 0,
                    "proposedSavoryMeals": 2,
                    "proposedDietaryRequirements": "2 mặn",
                    "modificationReason": "Hai cháu bận thi học kỳ"
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/registrations/{registrationId}/request-modification", EVENT_ID, REG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Gửi yêu cầu thay đổi thông tin đăng ký thành công"))
                .andExpect(jsonPath("$.data.status").value("PendingModification"))
                .andExpect(jsonPath("$.data.proposedTotalAttendees").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/registrations/{registrationId}/request-modification - 400 on blank reason")
    void requestModification_validationFailure() throws Exception {
        String payload = """
                {
                    "proposedTotalAttendees": 2,
                    "modificationReason": ""
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/registrations/{registrationId}/request-modification", EVENT_ID, REG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/registrations/{registrationId}/request-cancellation - 200 OK")
    void requestCancellation_success() throws Exception {
        RegistrationResponse pendingCancelResponse = new RegistrationResponse(
                REG_ID, EVENT_ID, REP_ID, "Nguyễn Văn Đại Diện",
                4, 1, 3, "1 chay", "Xe ô tô", Instant.now(),
                RegistrationStatus.PendingCancellation,
                null, null, null, null, null, "Gia đình bận việc đột xuất",
                null, null, null, null, List.of()
        );

        when(registrationService.requestCancellation(eq(EVENT_ID), eq(REG_ID), any(RequestCancelRegistrationRequest.class)))
                .thenReturn(pendingCancelResponse);

        String payload = """
                {
                    "cancellationReason": "Gia đình bận việc đột xuất"
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/registrations/{registrationId}/request-cancellation", EVENT_ID, REG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Gửi yêu cầu hủy đăng ký thành công"))
                .andExpect(jsonPath("$.data.status").value("PendingCancellation"));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/registrations/{registrationId}/review - 200 OK on approve")
    void reviewRegistration_approve_success() throws Exception {
        when(registrationService.reviewRegistrationRequest(eq(EVENT_ID), eq(REG_ID), any(ReviewRegistrationRequest.class)))
                .thenReturn(sampleResponse);

        String payload = """
                {
                    "approved": true
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/registrations/{registrationId}/review", EVENT_ID, REG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Xét duyệt yêu cầu đăng ký thành công"))
                .andExpect(jsonPath("$.data.status").value("Confirmed"));
    }

    @Test
    @DisplayName("POST /api/v1/events/{eventId}/registrations/{registrationId}/review - 400 on null approved")
    void reviewRegistration_validationFailure() throws Exception {
        String payload = """
                {
                    "rejectionReason": "Chưa duyệt"
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/registrations/{registrationId}/review", EVENT_ID, REG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
