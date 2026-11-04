package com.familyfund.identity.internal.controller;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.identity.api.PositionLinkService;
import com.familyfund.identity.api.dto.ClaimPositionRequest;
import com.familyfund.identity.api.dto.ClaimPositionResponse;
import com.familyfund.identity.api.dto.PositionLinkPreviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc contract tests for the redemption endpoints of a position link code (US-19):
 * {@code GET /api/v1/position-links/{inviteCode}} and {@code POST /api/v1/position-links/{inviteCode}/claim}.
 * The real filter chain, the {@code permitAll} rules and the database are covered by the integration test.
 */
@ExtendWith(MockitoExtension.class)
class PositionLinkClaimControllerTest {

    private static final String CODE = "8K9P2X7N";
    private static final String PREVIEW_PATH = "/api/v1/position-links/{inviteCode}";
    private static final String CLAIM_PATH = "/api/v1/position-links/{inviteCode}/claim";

    private static final String REGISTRATION = """
            {
                "phoneNumber": "0912345678",
                "password": "secret123",
                "email": "an@example.com"
            }
            """;

    private MockMvc mockMvc;

    @Mock
    private PositionLinkService positionLinkService;

    @InjectMocks
    private PositionLinkController positionLinkController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(positionLinkController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private static ClaimPositionResponse claimResponse() {
        return new ClaimPositionResponse(
                "ACC_7F3D9A0B1C2E",
                "0912345678",
                "an@example.com",
                "Active",
                Set.of(SystemRoles.MEMBER),
                "MEMK4F8ZQ1M7XB3QP",
                "Nguyen Van An",
                3,
                "BR_CHI_2",
                "Chi 2 - Nhánh Giáp",
                "CLAN_DEFAULT",
                "Dòng Họ Nguyễn",
                CODE,
                Instant.parse("2026-11-01T09:00:00Z"));
    }

    @Test
    @DisplayName("200: registering with a valid code returns the linked account and the branch it joined")
    void claim_ShouldReturn200() throws Exception {
        when(positionLinkService.claimPosition(eq(CODE), any(ClaimPositionRequest.class)))
                .thenReturn(claimResponse());

        mockMvc.perform(post(CLAIM_PATH, CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Liên kết tài khoản với vị trí phả hệ thành công"))
                .andExpect(jsonPath("$.data.accountId").value("ACC_7F3D9A0B1C2E"))
                .andExpect(jsonPath("$.data.accountStatus").value("Active"))
                .andExpect(jsonPath("$.data.memberId").value("MEMK4F8ZQ1M7XB3QP"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van An"))
                .andExpect(jsonPath("$.data.generation").value(3))
                .andExpect(jsonPath("$.data.branchId").value("BR_CHI_2"))
                .andExpect(jsonPath("$.data.branchName").value("Chi 2 - Nhánh Giáp"))
                .andExpect(jsonPath("$.data.clanId").value("CLAN_DEFAULT"))
                .andExpect(jsonPath("$.data.clanName").value("Dòng Họ Nguyễn"))
                .andExpect(jsonPath("$.data.inviteCode").value(CODE))
                .andExpect(jsonPath("$.data.roles[0]").value(SystemRoles.MEMBER))
                .andExpect(jsonPath("$.data.linkedAt").isNotEmpty());
    }

    @Test
    @DisplayName("200: the preview exposes the position without Requiring authentication")
    void preview_ShouldReturn200() throws Exception {
        when(positionLinkService.getPositionLinkPreview(CODE)).thenReturn(new PositionLinkPreviewResponse(
                CODE, "CLAN_DEFAULT", "Dòng Họ Nguyễn", "BR_CHI_2", "Chi 2 - Nhánh Giáp",
                "MEMK4F8ZQ1M7XB3QP", "Nguyen Van An", Instant.parse("2026-11-06T09:00:00Z")));

        mockMvc.perform(get(PREVIEW_PATH, CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy thông tin vị trí liên kết thành công"))
                .andExpect(jsonPath("$.data.inviteCode").value(CODE))
                .andExpect(jsonPath("$.data.clanName").value("Dòng Họ Nguyễn"))
                .andExpect(jsonPath("$.data.branchName").value("Chi 2 - Nhánh Giáp"))
                .andExpect(jsonPath("$.data.memberId").value("MEMK4F8ZQ1M7XB3QP"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van An"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("400: a blank password is rejected by Bean Validation and the service is never called")
    void blankPassword_ShouldReturn400() throws Exception {
        mockMvc.perform(post(CLAIM_PATH, CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRATION.replace("secret123", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.password").exists());

        verify(positionLinkService, never()).claimPosition(any(), any());
    }

    @Test
    @DisplayName("400: an invalid phone number and email are reported field by field")
    void invalidCredentials_ShouldReturn400() throws Exception {
        mockMvc.perform(post(CLAIM_PATH, CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phoneNumber": "12345",
                                    "password": "secret123",
                                    "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phoneNumber").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("400: malformed JSON body")
    void malformedBody_ShouldReturn400() throws Exception {
        mockMvc.perform(post(CLAIM_PATH, CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("404: an unknown or non-position-link code")
    void unknownCode_ShouldReturn404() throws Exception {
        when(positionLinkService.claimPosition(eq("NOPE0000"), any(ClaimPositionRequest.class)))
                .thenThrow(new ResourceNotFoundException("Mã liên kết vị trí", "inviteCode", "NOPE0000"));

        mockMvc.perform(post(CLAIM_PATH, "NOPE0000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRATION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("400: an expired or already used code")
    void deadCode_ShouldReturn400() throws Exception {
        when(positionLinkService.claimPosition(eq(CODE), any(ClaimPositionRequest.class)))
                .thenThrow(new BadRequestException("Mã liên kết vị trí đã được sử dụng hoặc đã bị vô hiệu hoá"));

        mockMvc.perform(post(CLAIM_PATH, CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRATION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("409: the position was already claimed")
    void positionAlreadyClaimed_ShouldReturn409() throws Exception {
        when(positionLinkService.claimPosition(eq(CODE), any(ClaimPositionRequest.class)))
                .thenThrow(new DuplicateResourceException("Vị trí phả hệ đã được nhận trước đó"));

        mockMvc.perform(post(CLAIM_PATH, CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRATION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("403: a suspended account cannot claim the position")
    void suspendedAccount_ShouldReturn403() throws Exception {
        when(positionLinkService.claimPosition(eq(CODE), any(ClaimPositionRequest.class)))
                .thenThrow(new AccessDeniedException("Tài khoản đang ở trạng thái Suspended"));

        mockMvc.perform(post(CLAIM_PATH, CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRATION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}