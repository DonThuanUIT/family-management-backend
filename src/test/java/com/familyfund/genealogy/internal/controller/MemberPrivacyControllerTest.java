package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.genealogy.internal.dto.request.UpdateMemberPrivacySettingRequest;
import com.familyfund.genealogy.internal.dto.response.MemberPrivacySettingResponse;
import com.familyfund.genealogy.internal.service.MemberPrivacySettingService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-25: MemberPrivacyController Unit Tests")
class MemberPrivacyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MemberPrivacySettingService privacySettingService;

    @InjectMocks
    private MemberPrivacyController privacyController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(privacyController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/members/me/privacy-settings returns 200 with current settings")
    void shouldReturnCurrentPrivacySettings() throws Exception {
        MemberPrivacySettingResponse response = MemberPrivacySettingResponse.builder()
                .memberId("MEM123456")
                .shareDateOfBirth(true)
                .sharePhoneNumber(false)
                .shareEmail(true)
                .updatedAt(Instant.parse("2026-11-11T12:00:00Z"))
                .build();

        when(privacySettingService.getMyPrivacySettings()).thenReturn(response);

        mockMvc.perform(get("/api/v1/members/me/privacy-settings")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value("MEM123456"))
                .andExpect(jsonPath("$.data.shareDateOfBirth").value(true))
                .andExpect(jsonPath("$.data.sharePhoneNumber").value(false))
                .andExpect(jsonPath("$.data.shareEmail").value(true));

        verify(privacySettingService).getMyPrivacySettings();
    }

    @Test
    @DisplayName("PUT /api/v1/members/me/privacy-settings updates settings successfully")
    void shouldUpdatePrivacySettingsSuccessfully() throws Exception {
        String payload = """
                {
                    "shareDateOfBirth": true,
                    "sharePhoneNumber": true,
                    "shareEmail": false
                }
                """;

        MemberPrivacySettingResponse response = MemberPrivacySettingResponse.builder()
                .memberId("MEM123456")
                .shareDateOfBirth(true)
                .sharePhoneNumber(true)
                .shareEmail(false)
                .updatedAt(Instant.parse("2026-11-11T12:05:00Z"))
                .build();

        when(privacySettingService.updateMyPrivacySettings(any(UpdateMemberPrivacySettingRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/members/me/privacy-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shareDateOfBirth").value(true))
                .andExpect(jsonPath("$.data.sharePhoneNumber").value(true))
                .andExpect(jsonPath("$.data.shareEmail").value(false));

        verify(privacySettingService).updateMyPrivacySettings(any(UpdateMemberPrivacySettingRequest.class));
    }

    @Test
    @DisplayName("PUT /api/v1/members/me/privacy-settings rejects null fields with 400 Bad Request")
    void shouldRejectInvalidPayload() throws Exception {
        String invalidPayload = """
                {
                    "shareDateOfBirth": null,
                    "sharePhoneNumber": true,
                    "shareEmail": false
                }
                """;

        mockMvc.perform(put("/api/v1/members/me/privacy-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
