package com.familyfund.event.internal.controller;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.event.api.dto.RegistrationStatus;
import com.familyfund.event.internal.dto.request.UpdateRegistrationRequest;
import com.familyfund.event.internal.dto.response.RegistrationResponse;
import com.familyfund.event.internal.service.RegistrationService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-41: RegistrationController MockMvc Tests")
class RegistrationControllerUs41Test {

    private MockMvc mockMvc;

    @Mock
    private RegistrationService registrationService;

    @InjectMocks
    private RegistrationController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("PUT /api/registrations/{id} trả về 200 OK khi cập nhật thành công")
    void updateRegistration_returns200() throws Exception {
        when(registrationService.updateRegistration(eq("REG_01"), any(UpdateRegistrationRequest.class)))
                .thenReturn(new RegistrationResponse(
                        "REG_01", "EVT_01", "MEM_REP", "Nguyễn Văn A",
                        4, 1, 3, null, null, null, RegistrationStatus.Confirmed,
                        null, null, null, null, null, null, null, null, null, null, java.util.List.of()));

        mockMvc.perform(put("/api/registrations/REG_01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "totalAttendees": 4,
                                  "vegetarianMeals": 1,
                                  "savoryMeals": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAttendees").value(4));
    }

    @Test
    @DisplayName("DELETE /api/registrations/{id} trả về 400 kèm thông báo liên hệ BTC khi đã chốt")
    void cancelRegistration_whenClosed_returns400() throws Exception {
        when(registrationService.cancelRegistration("REG_01"))
                .thenThrow(new BusinessRuleException(
                        "Danh sách tham dự đã được Ban tổ chức chốt. Vui lòng liên hệ trực tiếp Ban tổ chức sự kiện để được hỗ trợ."));

        mockMvc.perform(delete("/api/registrations/REG_01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("liên hệ trực tiếp Ban tổ chức")));
    }
}
