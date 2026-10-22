package com.familyfund.identity.internal.controller;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.identity.internal.dto.request.RegisterCreatorRequest;
import com.familyfund.identity.internal.dto.response.ClanResponse;
import com.familyfund.identity.internal.dto.response.CreatorRegistrationResponse;
import com.familyfund.identity.internal.dto.response.MemberResponse;
import com.familyfund.identity.internal.dto.response.UserAccountResponse;
import com.familyfund.identity.internal.service.CreatorRegistrationService;
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
import java.time.LocalDate;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CreatorRegistrationService creatorRegistrationService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-creator should return 201 Created on valid request")
    void registerCreator_Success_Returns201() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Hoàng",
                    "ancestorName": "Cụ Hoàng Phúc",
                    "originLocation": "Hà Nội",
                    "establishedYear": 1890,
                    "description": "Chi họ Hoàng đất kinh kỳ",
                    "fullName": "Hoàng Văn Nam",
                    "phoneNumber": "0912345678",
                    "password": "Password@123",
                    "gender": "Male",
                    "email": "hoangnam@example.com",
                    "dateOfBirth": "1992-06-20",
                    "generation": 3
                }
                """;

        UserAccountResponse accountResponse = new UserAccountResponse(
                "ACC_12345678",
                "0912345678",
                "hoangnam@example.com",
                "Active",
                Set.of("ROLE_CLAN_HEAD"),
                Instant.now()
        );
        ClanResponse clanResponse = new ClanResponse(
                "CLAN_87654321",
                "Dòng họ Hoàng",
                "Cụ Hoàng Phúc",
                "Hà Nội",
                1890,
                "Chi họ Hoàng đất kinh kỳ"
        );
        MemberResponse memberResponse = new MemberResponse(
                "MEM_11223344",
                "Hoàng Văn Nam",
                "Male",
                3,
                LocalDate.of(1992, 6, 20),
                "BR_99887766"
        );
        CreatorRegistrationResponse mockResponse = new CreatorRegistrationResponse(
                accountResponse,
                clanResponse,
                memberResponse
        );

        when(creatorRegistrationService.registerCreator(any(RegisterCreatorRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng ký người khởi tạo và tạo dòng họ mới thành công"))
                .andExpect(jsonPath("$.data.account.accountId").value("ACC_12345678"))
                .andExpect(jsonPath("$.data.account.phoneNumber").value("0912345678"))
                .andExpect(jsonPath("$.data.account.roles[0]").value("ROLE_CLAN_HEAD"))
                .andExpect(jsonPath("$.data.clan.clanId").value("CLAN_87654321"))
                .andExpect(jsonPath("$.data.clan.clanName").value("Dòng họ Hoàng"))
                .andExpect(jsonPath("$.data.member.memberId").value("MEM_11223344"))
                .andExpect(jsonPath("$.data.member.fullName").value("Hoàng Văn Nam"));

        verify(creatorRegistrationService).registerCreator(any(RegisterCreatorRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-creator should return 400 Bad Request when required fields are missing")
    void registerCreator_MissingFields_Returns400() throws Exception {
        String invalidPayload = """
                {
                    "clanName": "",
                    "fullName": "",
                    "phoneNumber": "123",
                    "password": "123",
                    "gender": "Unknown"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register-creator should return 400 when phone number is duplicated")
    void registerCreator_DuplicatePhone_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Đỗ",
                    "fullName": "Đỗ Nam",
                    "phoneNumber": "0987654321",
                    "password": "Password@123",
                    "gender": "Male"
                }
                """;

        when(creatorRegistrationService.registerCreator(any(RegisterCreatorRequest.class)))
                .thenThrow(new BadRequestException("Số điện thoại '0987654321' đã được đăng ký tài khoản"));

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("đã được đăng ký tài khoản")));
    }
}
