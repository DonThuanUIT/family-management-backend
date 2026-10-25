package com.familyfund.identity.internal.controller;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.identity.internal.dto.request.LoginRequest;
import com.familyfund.identity.internal.dto.request.RefreshTokenRequest;
import com.familyfund.identity.internal.dto.request.RegisterCreatorRequest;
import com.familyfund.identity.internal.dto.response.AuthTokenResponse;
import com.familyfund.identity.internal.dto.response.ClanResponse;
import com.familyfund.identity.internal.dto.response.CreatorRegistrationResponse;
import com.familyfund.identity.internal.dto.response.MemberResponse;
import com.familyfund.identity.internal.dto.response.UserAccountResponse;
import com.familyfund.identity.internal.service.AuthService;
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
import static org.mockito.Mockito.never;
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

    @Mock
    private AuthService authService;

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

    private CreatorRegistrationResponse buildMockResponse(String clanName, String fullName, String phone, String email, String gender, Integer gen) {
        return new CreatorRegistrationResponse(
                new UserAccountResponse("ACC_12345678", phone, email, "Active", Set.of("ROLE_CLAN_HEAD"), Instant.now()),
                new ClanResponse("CLAN_87654321", clanName, null, null, null, null),
                new MemberResponse("MEM_11223344", fullName, gender, gen != null ? gen : 1, null, "BR_99887766")
        );
    }

    // =========================================================================
    // Normal Test Cases
    // =========================================================================

    @Test
    @DisplayName("Normal: POST /api/v1/auth/register-creator should return 201 Created on full valid request")
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

        when(creatorRegistrationService.registerCreator(any(RegisterCreatorRequest.class)))
                .thenReturn(buildMockResponse("Dòng họ Hoàng", "Hoàng Văn Nam", "0912345678", "hoangnam@example.com", "Male", 3));

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
    @DisplayName("Normal: POST /api/v1/auth/register-creator should return 201 Created on minimal payload (mandatory fields only)")
    void registerCreator_MinimalPayload_Returns201() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Lê",
                    "fullName": "Lê Văn Minh",
                    "phoneNumber": "0987654321",
                    "password": "Password@123",
                    "gender": "Female"
                }
                """;

        when(creatorRegistrationService.registerCreator(any(RegisterCreatorRequest.class)))
                .thenReturn(buildMockResponse("Dòng họ Lê", "Lê Văn Minh", "0987654321", null, "Female", 1));

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.account.phoneNumber").value("0987654321"))
                .andExpect(jsonPath("$.data.clan.clanName").value("Dòng họ Lê"))
                .andExpect(jsonPath("$.data.member.gender").value("Female"));

        verify(creatorRegistrationService).registerCreator(any(RegisterCreatorRequest.class));
    }

    // =========================================================================
    // Boundary Cases
    // =========================================================================

    @Test
    @DisplayName("Boundary: Password exactly 6 characters should pass validation and return 201")
    void registerCreator_PasswordBoundary_6Chars_Returns201() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Vũ",
                    "fullName": "Vũ Văn A",
                    "phoneNumber": "0911223344",
                    "password": "123456",
                    "gender": "Male"
                }
                """;

        when(creatorRegistrationService.registerCreator(any(RegisterCreatorRequest.class)))
                .thenReturn(buildMockResponse("Dòng họ Vũ", "Vũ Văn A", "0911223344", null, "Male", 1));

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(creatorRegistrationService).registerCreator(any(RegisterCreatorRequest.class));
    }

    @Test
    @DisplayName("Boundary: Phone number exactly 8 characters (min valid length) should pass validation and return 201")
    void registerCreator_PhoneBoundary_8Digits_Returns201() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Đinh",
                    "fullName": "Đinh Bộ Lĩnh",
                    "phoneNumber": "12345678",
                    "password": "Password@123",
                    "gender": "Male"
                }
                """;

        when(creatorRegistrationService.registerCreator(any(RegisterCreatorRequest.class)))
                .thenReturn(buildMockResponse("Dòng họ Đinh", "Đinh Bộ Lĩnh", "12345678", null, "Male", 1));

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(creatorRegistrationService).registerCreator(any(RegisterCreatorRequest.class));
    }

    @Test
    @DisplayName("Boundary: Phone number exactly 15 characters (max valid length) should pass validation and return 201")
    void registerCreator_PhoneBoundary_15Digits_Returns201() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Đinh",
                    "fullName": "Đinh Bộ Lĩnh",
                    "phoneNumber": "012345678901234",
                    "password": "Password@123",
                    "gender": "Male"
                }
                """;

        when(creatorRegistrationService.registerCreator(any(RegisterCreatorRequest.class)))
                .thenReturn(buildMockResponse("Dòng họ Đinh", "Đinh Bộ Lĩnh", "012345678901234", null, "Male", 1));

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(creatorRegistrationService).registerCreator(any(RegisterCreatorRequest.class));
    }

    @Test
    @DisplayName("Boundary: Phone number with leading '+' sign (E.164) should pass validation and return 201")
    void registerCreator_PhoneWithPlusSign_Returns201() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Lý",
                    "fullName": "Lý Thường Kiệt",
                    "phoneNumber": "+84912345678",
                    "password": "Password@123",
                    "gender": "Male"
                }
                """;

        when(creatorRegistrationService.registerCreator(any(RegisterCreatorRequest.class)))
                .thenReturn(buildMockResponse("Dòng họ Lý", "Lý Thường Kiệt", "+84912345678", null, "Male", 1));

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(creatorRegistrationService).registerCreator(any(RegisterCreatorRequest.class));
    }

    @Test
    @DisplayName("Boundary: Generation = 1 (minimum allowed) should pass validation and return 201")
    void registerCreator_GenerationBoundary_1_Returns201() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Trịnh",
                    "fullName": "Trịnh Kiểm",
                    "phoneNumber": "0988776655",
                    "password": "Password@123",
                    "gender": "Male",
                    "generation": 1
                }
                """;

        when(creatorRegistrationService.registerCreator(any(RegisterCreatorRequest.class)))
                .thenReturn(buildMockResponse("Dòng họ Trịnh", "Trịnh Kiểm", "0988776655", null, "Male", 1));

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(creatorRegistrationService).registerCreator(any(RegisterCreatorRequest.class));
    }

    // =========================================================================
    // Abnormal / Edge Cases (Validation Failures)
    // =========================================================================

    @Test
    @DisplayName("Abnormal: Password shorter than 6 characters (5 chars) should return 400 Bad Request")
    void registerCreator_ShortPassword_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Nguyễn",
                    "fullName": "Nguyễn Văn A",
                    "phoneNumber": "0912345678",
                    "password": "12345",
                    "gender": "Male"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request payload"))
                .andExpect(jsonPath("$.errors.password").value("Mật khẩu phải có độ dài tối thiểu 6 ký tự"));

        verify(creatorRegistrationService, never()).registerCreator(any());
    }

    @Test
    @DisplayName("Abnormal: Phone number shorter than 8 characters (7 digits) should return 400 Bad Request")
    void registerCreator_PhoneTooShort_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Nguyễn",
                    "fullName": "Nguyễn Văn A",
                    "phoneNumber": "1234567",
                    "password": "Password@123",
                    "gender": "Male"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request payload"))
                .andExpect(jsonPath("$.errors.phoneNumber").value("Số điện thoại không đúng định dạng (8-15 ký tự số)"));

        verify(creatorRegistrationService, never()).registerCreator(any());
    }

    @Test
    @DisplayName("Abnormal: Phone number longer than 15 characters (16 digits) should return 400 Bad Request")
    void registerCreator_PhoneTooLong_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Nguyễn",
                    "fullName": "Nguyễn Văn A",
                    "phoneNumber": "0123456789012345",
                    "password": "Password@123",
                    "gender": "Male"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request payload"))
                .andExpect(jsonPath("$.errors.phoneNumber").value("Số điện thoại không đúng định dạng (8-15 ký tự số)"));

        verify(creatorRegistrationService, never()).registerCreator(any());
    }

    @Test
    @DisplayName("Abnormal: Phone number containing invalid characters should return 400 Bad Request")
    void registerCreator_PhoneWithLetters_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Nguyễn",
                    "fullName": "Nguyễn Văn A",
                    "phoneNumber": "091234abcd",
                    "password": "Password@123",
                    "gender": "Male"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request payload"))
                .andExpect(jsonPath("$.errors.phoneNumber").value("Số điện thoại không đúng định dạng (8-15 ký tự số)"));

        verify(creatorRegistrationService, never()).registerCreator(any());
    }

    @Test
    @DisplayName("Abnormal: Generation = 0 or negative should return 400 Bad Request")
    void registerCreator_InvalidGeneration_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Nguyễn",
                    "fullName": "Nguyễn Văn A",
                    "phoneNumber": "0912345678",
                    "password": "Password@123",
                    "gender": "Male",
                    "generation": 0
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request payload"))
                .andExpect(jsonPath("$.errors.generation").value("Đời / thế hệ phải từ 1 trở lên"));

        verify(creatorRegistrationService, never()).registerCreator(any());
    }

    @Test
    @DisplayName("Abnormal: Malformed email should return 400 Bad Request")
    void registerCreator_InvalidEmail_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Nguyễn",
                    "fullName": "Nguyễn Văn A",
                    "phoneNumber": "0912345678",
                    "password": "Password@123",
                    "gender": "Male",
                    "email": "not-a-valid-email"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request payload"))
                .andExpect(jsonPath("$.errors.email").value("Địa chỉ email không đúng định dạng"));

        verify(creatorRegistrationService, never()).registerCreator(any());
    }

    @Test
    @DisplayName("Abnormal: Invalid gender value should return 400 Bad Request")
    void registerCreator_InvalidGender_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Nguyễn",
                    "fullName": "Nguyễn Văn A",
                    "phoneNumber": "0912345678",
                    "password": "Password@123",
                    "gender": "UnknownGender"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request payload"))
                .andExpect(jsonPath("$.errors.gender").value("Giới tính phải là Male, Female hoặc Other"));

        verify(creatorRegistrationService, never()).registerCreator(any());
    }

    @Test
    @DisplayName("Abnormal: Blank mandatory clanName should return 400 Bad Request")
    void registerCreator_BlankClanName_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "   ",
                    "fullName": "Nguyễn Văn A",
                    "phoneNumber": "0912345678",
                    "password": "Password@123",
                    "gender": "Male"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request payload"))
                .andExpect(jsonPath("$.errors.clanName").value("Tên dòng họ không được để trống"));

        verify(creatorRegistrationService, never()).registerCreator(any());
    }

    @Test
    @DisplayName("Abnormal: Blank mandatory fullName should return 400 Bad Request")
    void registerCreator_BlankFullName_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Nguyễn",
                    "fullName": "   ",
                    "phoneNumber": "0912345678",
                    "password": "Password@123",
                    "gender": "Male"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request payload"))
                .andExpect(jsonPath("$.errors.fullName").value("Họ và tên người khởi tạo không được để trống"));

        verify(creatorRegistrationService, never()).registerCreator(any());
    }

    @Test
    @DisplayName("Abnormal: Missing all required fields should return 400 Bad Request")
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

        verify(creatorRegistrationService, never()).registerCreator(any());
    }

    @Test
    @DisplayName("Abnormal: POST /api/v1/auth/register-creator should return 400 when phone number is duplicated in service")
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
                .andExpect(jsonPath("$.message").value(containsString("Số điện thoại '0987654321' đã được đăng ký tài khoản")));
    }

    @Test
    @DisplayName("Abnormal: POST /api/v1/auth/register-creator should return 400 when email is duplicated in service")
    void registerCreator_DuplicateEmail_Returns400() throws Exception {
        String payload = """
                {
                    "clanName": "Dòng họ Đỗ",
                    "fullName": "Đỗ Nam",
                    "phoneNumber": "0987654321",
                    "password": "Password@123",
                    "gender": "Male",
                    "email": "dup@example.com"
                }
                """;

        when(creatorRegistrationService.registerCreator(any(RegisterCreatorRequest.class)))
                .thenThrow(new BadRequestException("Địa chỉ email 'dup@example.com' đã được đăng ký tài khoản"));

        mockMvc.perform(post("/api/v1/auth/register-creator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Địa chỉ email 'dup@example.com' đã được đăng ký tài khoản")));
    }

    // =========================================================================
    // Auth Login & Refresh Token Tests
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/auth/login should return 200 OK with AuthTokenResponse on valid credentials")
    void login_Success_Returns200() throws Exception {
        String payload = """
                {
                    "loginIdentifier": "0912345678",
                    "password": "Password@123"
                }
                """;

        AuthTokenResponse mockResponse = new AuthTokenResponse(
                "access.token",
                "refresh.token",
                "Bearer",
                3600,
                "ACC_001",
                "0912345678",
                "user@example.com",
                "Nguyễn Văn An",
                "CLAN_001",
                "MEM_001",
                Set.of("ROLE_CLAN_HEAD")
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công"))
                .andExpect(jsonPath("$.data.accessToken").value("access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh.token"))
                .andExpect(jsonPath("$.data.accountId").value("ACC_001"))
                .andExpect(jsonPath("$.data.clanId").value("CLAN_001"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_CLAN_HEAD"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should return 400 when missing credentials")
    void login_MissingFields_Returns400() throws Exception {
        String invalidPayload = """
                {
                    "loginIdentifier": "",
                    "password": ""
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh-token should return 200 OK on valid refresh token")
    void refreshToken_Success_Returns200() throws Exception {
        String payload = """
                {
                    "refreshToken": "valid.refresh.token"
                }
                """;

        AuthTokenResponse mockResponse = new AuthTokenResponse(
                "new.access.token",
                "valid.refresh.token",
                "Bearer",
                3600,
                "ACC_001",
                "0912345678",
                "user@example.com",
                "Nguyễn Văn An",
                "CLAN_001",
                "MEM_001",
                Set.of("ROLE_CLAN_HEAD")
        );

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Làm mới token thành công"))
                .andExpect(jsonPath("$.data.accessToken").value("new.access.token"));

        verify(authService).refreshToken(any(RefreshTokenRequest.class));
    }
}
