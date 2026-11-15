package com.familyfund.identity.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.identity.internal.dto.request.ChangePasswordRequest;
import com.familyfund.identity.internal.dto.request.UpdateContactProfileRequest;
import com.familyfund.identity.internal.dto.response.UserProfileResponse;
import com.familyfund.identity.internal.service.ProfileService;
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

import java.time.LocalDate;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProfileService profileService;

    @InjectMocks
    private ProfileController profileController;

    private UserProfileResponse mockProfileResponse;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(profileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockProfileResponse = UserProfileResponse.builder()
                .accountId("ACC_001")
                .memberId("MEM_001")
                .clanId("CLAN_DEFAULT")
                .accountStatus("Active")
                .roles(Set.of("ROLE_MEMBER"))
                .phoneNumber("0912345678")
                .email("tuan.le@familyfund.vn")
                .address("Hà Đông, Hà Nội")
                .fullName("Lê Hữu Tuấn")
                .dateOfBirth(LocalDate.of(1990, 8, 15))
                .gender("Male")
                .generation(5)
                .branchId("BR_CHI_2")
                .branchName("Chi 2 - Nhánh Giáp")
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/profile/me thành công trả về 200 OK và thông tin hồ sơ")
    void getMyProfile_V1_Success() throws Exception {
        when(profileService.getMyProfile()).thenReturn(mockProfileResponse);

        mockMvc.perform(get("/api/v1/profile/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountId").value("ACC_001"))
                .andExpect(jsonPath("$.data.fullName").value("Lê Hữu Tuấn"))
                .andExpect(jsonPath("$.data.phoneNumber").value("0912345678"))
                .andExpect(jsonPath("$.data.generation").value(5));
    }

    @Test
    @DisplayName("GET /api/profile/me thành công (hỗ trợ alias path)")
    void getMyProfile_Alias_Success() throws Exception {
        when(profileService.getMyProfile()).thenReturn(mockProfileResponse);

        mockMvc.perform(get("/api/profile/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountId").value("ACC_001"));
    }

    @Test
    @DisplayName("PUT /api/v1/profile/me thành công khi dữ liệu hợp lệ")
    void updateContactProfile_Success() throws Exception {
        String payload = """
                {
                    "phoneNumber": "0987654321",
                    "email": "updated@familyfund.vn",
                    "address": "Ba Đình, Hà Nội"
                }
                """;

        when(profileService.updateContactProfile(any())).thenReturn(mockProfileResponse);

        mockMvc.perform(put("/api/v1/profile/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật thông tin liên lạc thành công"));

        verify(profileService).updateContactProfile(any());
    }

    @Test
    @DisplayName("PUT /api/v1/profile/me trả về 400 Bad Request khi số điện thoại không hợp lệ")
    void updateContactProfile_InvalidPhone_Returns400() throws Exception {
        String payload = """
                {
                    "phoneNumber": "12345",
                    "email": "updated@familyfund.vn",
                    "address": "Ba Đình, Hà Nội"
                }
                """;

        mockMvc.perform(put("/api/v1/profile/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/profile/change-password thành công trả về 200 OK")
    void changePassword_Success() throws Exception {
        String payload = """
                {
                    "currentPassword": "OldPassword@123",
                    "newPassword": "NewPassword@456",
                    "confirmPassword": "NewPassword@456"
                }
                """;

        mockMvc.perform(post("/api/v1/profile/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công"));

        verify(profileService).changePassword(any());
    }

    @Test
    @DisplayName("POST /api/v1/profile/change-password trả về 400 khi mật khẩu mới quá ngắn (< 6 ký tự)")
    void changePassword_ShortPassword_Returns400() throws Exception {
        String payload = """
                {
                    "currentPassword": "OldPassword@123",
                    "newPassword": "123",
                    "confirmPassword": "123"
                }
                """;

        mockMvc.perform(post("/api/v1/profile/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }
}
