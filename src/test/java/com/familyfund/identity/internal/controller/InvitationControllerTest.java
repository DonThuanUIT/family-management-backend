package com.familyfund.identity.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.identity.api.InvitationService;
import com.familyfund.identity.api.dto.CreateGeneralInviteRequest;
import com.familyfund.identity.api.dto.InviteCodeResponse;
import com.familyfund.identity.internal.entity.InviteType;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InvitationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InvitationService invitationService;

    @InjectMocks
    private InvitationController invitationController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(invitationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/invitations/general should create invite and return 201 Created")
    void createGeneralInvite_ShouldReturn201() throws Exception {
        String jsonPayload = """
                {
                    "targetBranchId": "BRANCH_01",
                    "maxUses": 50,
                    "validityDays": 10
                }
                """;

        InviteCodeResponse mockResponse = InviteCodeResponse.builder()
                .inviteCode("8K9P2X7N")
                .inviteType(InviteType.GeneralClanInvite)
                .shareableUrl("http://localhost:3000/join?code=8K9P2X7N")
                .maxUses(50)
                .usedCount(0)
                .isActive(true)
                .createdByAccountId("ACC_TEST")
                .build();

        when(invitationService.createGeneralClanInvite(any(CreateGeneralInviteRequest.class), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/invitations/general")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/invitations/8K9P2X7N")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inviteCode").value("8K9P2X7N"))
                .andExpect(jsonPath("$.data.inviteType").value("GeneralClanInvite"));
    }

    @Test
    @DisplayName("POST /api/v1/invitations/general with invalid maxUses should return 400 Bad Request")
    void createGeneralInvite_InvalidMaxUses_ShouldReturn400() throws Exception {
        String invalidPayload = """
                {
                    "maxUses": 0
                }
                """;

        mockMvc.perform(post("/api/v1/invitations/general")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.maxUses").exists());
    }

    @Test
    @DisplayName("GET /api/v1/invitations/general should return 200 OK with invite list")
    void getActiveGeneralInvites_ShouldReturn200() throws Exception {
        InviteCodeResponse code1 = InviteCodeResponse.builder().inviteCode("CODE1").build();
        InviteCodeResponse code2 = InviteCodeResponse.builder().inviteCode("CODE2").build();

        when(invitationService.getActiveGeneralInvites()).thenReturn(List.of(code1, code2));

        mockMvc.perform(get("/api/v1/invitations/general"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].inviteCode").value("CODE1"));
    }

    @Test
    @DisplayName("GET /api/v1/invitations/{inviteCode} should return 200 OK for valid code")
    void getInviteByCode_Valid_ShouldReturn200() throws Exception {
        String code = "8K9P2X7N";
        InviteCodeResponse response = InviteCodeResponse.builder()
                .inviteCode(code)
                .inviteType(InviteType.GeneralClanInvite)
                .shareableUrl("http://localhost:3000/join?code=" + code)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .isActive(true)
                .build();

        when(invitationService.getInviteByCode(code)).thenReturn(response);

        mockMvc.perform(get("/api/v1/invitations/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inviteCode").value(code));
    }

    @Test
    @DisplayName("GET /api/v1/invitations/{inviteCode} should return 404 when not found")
    void getInviteByCode_NotFound_ShouldReturn404() throws Exception {
        String code = "NOT_FOUND";
        when(invitationService.getInviteByCode(code))
                .thenThrow(new ResourceNotFoundException("Mã mời", "inviteCode", code));

        mockMvc.perform(get("/api/v1/invitations/" + code))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/invitations/{inviteCode}/deactivate should return 200 OK")
    void deactivateInvite_ShouldReturn200() throws Exception {
        String code = "8K9P2X7N";

        mockMvc.perform(patch("/api/v1/invitations/" + code + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Vô hiệu hóa mã mời thành công"));

        verify(invitationService).deactivateInviteCode(eq(code), anyString());
    }
}

