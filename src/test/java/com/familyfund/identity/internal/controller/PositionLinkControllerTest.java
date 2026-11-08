package com.familyfund.identity.internal.controller;

import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.api.dto.VirtualMemberDto;
import com.familyfund.identity.api.PositionLinkService;
import com.familyfund.identity.api.dto.CreateVirtualMemberRequest;
import com.familyfund.identity.api.dto.CreateVirtualMemberResponse;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc contract tests for {@code POST /api/v1/branches/{branchId}/virtual-members} (US-18).
 * Method security and the real filter chain are covered by the integration test.
 */
@ExtendWith(MockitoExtension.class)
class PositionLinkControllerTest {

    private static final String PATH = "/api/v1/branches/{branchId}/virtual-members";

    private static final String VALID_PAYLOAD = """
            {
                "fullName": "Nguyen Van An",
                "dateOfBirth": "1990-05-20",
                "gender": "Male",
                "generation": 3,
                "validityDays": 10
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

    private static CreateVirtualMemberResponse response(String inviteCode) {
        VirtualMemberDto member = new VirtualMemberDto(
                "MEMPL000000000001", "Nguyen Van An", LocalDate.of(1990, 5, 20), "Male", 3, "Living", null,
                "BR_CHI_2", "Chi 2 - Nhánh Giáp", "CLAN_DEFAULT", true, Instant.parse("2026-10-31T00:00:00Z"));

        InviteCodeResponse code = InviteCodeResponse.builder()
                .inviteCode(inviteCode)
                .clanId("CLAN_DEFAULT")
                .clanName("Dòng Họ Nguyễn")
                .inviteType(InviteType.PositionLinkCode)
                .shareableUrl("http://localhost:3000/position-link?code=" + inviteCode)
                .targetBranchId("BR_CHI_2")
                .targetBranchName("Chi 2 - Nhánh Giáp")
                .targetMemberId("MEMPL000000000001")
                .maxUses(1)
                .usedCount(0)
                .isActive(true)
                .createdByAccountId("ACC_TRUONG_TOC")
                .build();

        return new CreateVirtualMemberResponse(member, code);
    }

    @Test
    @DisplayName("201: creates the virtual profile and the position link, Location points at the code")
    void create_ShouldReturn201() throws Exception {
        when(positionLinkService.createVirtualMemberWithPositionLink(eq("BR_CHI_2"), any(CreateVirtualMemberRequest.class)))
                .thenReturn(response("8K9P2X7N"));

        mockMvc.perform(post(PATH, "BR_CHI_2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/invitations/8K9P2X7N"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tạo hồ sơ ảo và mã liên kết vị trí thành công"))
                .andExpect(jsonPath("$.data.member.memberId").value("MEMPL000000000001"))
                .andExpect(jsonPath("$.data.member.branchId").value("BR_CHI_2"))
                .andExpect(jsonPath("$.data.member.branchName").value("Chi 2 - Nhánh Giáp"))
                .andExpect(jsonPath("$.data.member.clanId").value("CLAN_DEFAULT"))
                .andExpect(jsonPath("$.data.member.isPlaceholder").value(true))
                .andExpect(jsonPath("$.data.positionLinkCode.inviteCode").value("8K9P2X7N"))
                .andExpect(jsonPath("$.data.positionLinkCode.inviteType").value("PositionLinkCode"))
                .andExpect(jsonPath("$.data.positionLinkCode.maxUses").value(1))
                .andExpect(jsonPath("$.data.positionLinkCode.usedCount").value(0))
                .andExpect(jsonPath("$.data.positionLinkCode.isActive").value(true))
                .andExpect(jsonPath("$.data.positionLinkCode.targetMemberId").value("MEMPL000000000001"))
                .andExpect(jsonPath("$.data.positionLinkCode.shareableUrl")
                        .value("http://localhost:3000/position-link?code=8K9P2X7N"));
    }

    @Test
    @DisplayName("400: blank fullName is rejected by Bean Validation")
    void blankFullName_ShouldReturn400() throws Exception {
        mockMvc.perform(post(PATH, "BR_CHI_2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("Nguyen Van An", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    @DisplayName("400: gender outside the CHECK list, generation below 1 and a non-future expiry are rejected")
    void invalidPayload_ShouldReturn400() throws Exception {
        mockMvc.perform(post(PATH, "BR_CHI_2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Nguyen Van An",
                                    "gender": "male",
                                    "generation": 0,
                                    "expiresAt": "%s"
                                }
                                """.formatted(Instant.now().minus(1, ChronoUnit.DAYS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.gender").exists())
                .andExpect(jsonPath("$.errors.generation").exists())
                .andExpect(jsonPath("$.errors.expiresAt").exists());
    }

    @Test
    @DisplayName("400: malformed JSON body")
    void malformedBody_ShouldReturn400() throws Exception {
        mockMvc.perform(post(PATH, "BR_CHI_2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("403: caller outside the branch scope (AccessDeniedException from the service)")
    void accessDenied_ShouldReturn403() throws Exception {
        when(positionLinkService.createVirtualMemberWithPositionLink(anyString(), any(CreateVirtualMemberRequest.class)))
                .thenThrow(new AccessDeniedException("Branch head may only add members to the branch they manage"));

        mockMvc.perform(post(PATH, "BR_CHI_2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("404: unknown branch")
    void unknownBranch_ShouldReturn404() throws Exception {
        when(positionLinkService.createVirtualMemberWithPositionLink(eq("BR_NOPE"), any(CreateVirtualMemberRequest.class)))
                .thenThrow(new ResourceNotFoundException("Branch not found with ID: BR_NOPE"));

        mockMvc.perform(post(PATH, "BR_NOPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("409: the same virtual profile already exists in the clan")
    void duplicateProfile_ShouldReturn409() throws Exception {
        when(positionLinkService.createVirtualMemberWithPositionLink(anyString(), any(CreateVirtualMemberRequest.class)))
                .thenThrow(new DuplicateResourceException("A member profile with the same full name already exists"));

        mockMvc.perform(post(PATH, "BR_CHI_2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
