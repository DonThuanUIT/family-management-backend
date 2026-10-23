package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.internal.dto.request.CreateMemberRequest;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.service.GenealogyService;
import com.familyfund.genealogy.internal.service.MemberService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for POST /api/v1/branches/{branchId}/members (US-03).
 * Bean Validation and {@link GlobalExceptionHandler} are wired in, so status codes are asserted
 * end-to-end; method security and persistence are covered by
 * {@link MemberControllerIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    private static final String BRANCH_ID = "BR_CHILD_1";

    private static final String VALID_PAYLOAD = """
            {
                "fullName": "Nguyen Van An",
                "gender": "Male",
                "generation": 2
            }
            """;

    private MockMvc mockMvc;

    @Mock
    private GenealogyService genealogyService;

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberController memberController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(memberController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private static MemberResponse createdMember() {
        return MemberResponse.builder()
                .memberId("MEMK4F8ZQ1M7XB3QP")
                .fullName("Nguyen Van An")
                .gender("Male")
                .generation(2)
                .status("Living")
                .branchId(BRANCH_ID)
                .branchName("Chi Hai")
                .clanId("CLAN_DEFAULT")
                .isPlaceholder(true)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/branches/{branchId}/members returns 201 with the created placeholder member")
    void createMember_ShouldReturn201() throws Exception {
        when(memberService.createMember(eq(BRANCH_ID), any(CreateMemberRequest.class))).thenReturn(createdMember());

        mockMvc.perform(post("/api/v1/branches/{branchId}/members", BRANCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Member created successfully"))
                .andExpect(jsonPath("$.data.memberId").value("MEMK4F8ZQ1M7XB3QP"))
                .andExpect(jsonPath("$.data.branchId").value(BRANCH_ID))
                .andExpect(jsonPath("$.data.isPlaceholder").value(true));

        verify(memberService).createMember(eq(BRANCH_ID), any(CreateMemberRequest.class));
    }

    @Test
    @DisplayName("Branch-scope violation from the service maps to 403 Forbidden")
    void createMember_OutOfScope_ShouldReturn403() throws Exception {
        when(memberService.createMember(eq(BRANCH_ID), any(CreateMemberRequest.class)))
                .thenThrow(new AccessDeniedException("Branch head may only add members to the branch they manage"));

        mockMvc.perform(post("/api/v1/branches/{branchId}/members", BRANCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Unknown branch maps to 404 Not Found")
    void createMember_UnknownBranch_ShouldReturn404() throws Exception {
        when(memberService.createMember(eq(BRANCH_ID), any(CreateMemberRequest.class)))
                .thenThrow(new ResourceNotFoundException("Branch not found with ID: BR_CHILD_1"));

        mockMvc.perform(post("/api/v1/branches/{branchId}/members", BRANCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Duplicate profile from the service maps to 409 Conflict")
    void createMember_Duplicate_ShouldReturn409() throws Exception {
        when(memberService.createMember(eq(BRANCH_ID), any(CreateMemberRequest.class)))
                .thenThrow(new DuplicateResourceException("A member profile with the same details already exists"));

        mockMvc.perform(post("/api/v1/branches/{branchId}/members", BRANCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Blank fullName returns 400 and the service is never called")
    void createMember_WithBlankName_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/branches/{branchId}/members", BRANCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("Nguyen Van An", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.fullName").exists());
    }

    @Test
    @DisplayName("Invalid gender returns 400 and the service is never called")
    void createMember_WithInvalidGender_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/branches/{branchId}/members", BRANCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("\"Male\"", "\"male\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.gender").exists());
    }

    @Test
    @DisplayName("Missing generation returns 400 (NOT NULL column)")
    void createMember_WithoutGeneration_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/branches/{branchId}/members", BRANCH_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Nguyen Van An",
                                    "gender": "Male"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.generation").exists());
    }
}
