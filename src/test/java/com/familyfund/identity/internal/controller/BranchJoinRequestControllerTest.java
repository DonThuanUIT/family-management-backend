package com.familyfund.identity.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.identity.api.BranchJoinRequestService;
import com.familyfund.identity.api.dto.JoinRequestResponse;
import com.familyfund.identity.api.dto.RejectJoinRequest;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import com.familyfund.identity.internal.entity.ProposalType;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.time.LocalDate;
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
class BranchJoinRequestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BranchJoinRequestService branchJoinRequestService;

    @InjectMocks
    private BranchJoinRequestController branchJoinRequestController;

    private JoinRequestResponse mockResponse;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(branchJoinRequestController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        mockResponse = JoinRequestResponse.builder()
                .requestId("REQ_001")
                .accountId("ACC_001")
                .clanId("CLAN_01")
                .inviteCode("8K9P2X7N")
                .fullName("Nguyễn Văn A")
                .phoneNumber("0912345678")
                .email("test@domain.com")
                .dateOfBirth(LocalDate.of(1995, 5, 20))
                .gender("Male")
                .proposalType(ProposalType.ExistingBranchLeaf)
                .targetBranchId("BR_ROOT")
                .targetBranchName("Chi Gốc")
                .proposedGeneration(4)
                .approvalStatus(JoinRequestApprovalStatus.Pending)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/join-requests should return paged requests with 200 OK")
    void getJoinRequests_Success() throws Exception {
        Pageable pageable = PageRequest.of(0, 10);
        Page<JoinRequestResponse> pagedResponse = new PageImpl<>(List.of(mockResponse), pageable, 1);
        when(branchJoinRequestService.getJoinRequests(eq(JoinRequestApprovalStatus.Pending), eq("BR_ROOT"), any(Pageable.class), any()))
                .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/join-requests")
                        .param("status", "Pending")
                        .param("branchId", "BR_ROOT")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].requestId").value("REQ_001"))
                .andExpect(jsonPath("$.data.content[0].fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.data.content[0].targetBranchName").value("Chi Gốc"));
    }

    @Test
    @DisplayName("POST /api/v1/join-requests/{requestId}/approve should return 200 OK")
    void approveJoinRequest_Success() throws Exception {
        JoinRequestResponse approvedResponse = JoinRequestResponse.builder()
                .requestId("REQ_001")
                .accountId("ACC_001")
                .clanId("CLAN_01")
                .fullName("Nguyễn Văn A")
                .approvalStatus(JoinRequestApprovalStatus.Approved)
                .reviewedByAccountId("ACC_ADMIN")
                .reviewedAt(Instant.now())
                .build();

        when(branchJoinRequestService.approveJoinRequest(eq("REQ_001"), any()))
                .thenReturn(approvedResponse);

        mockMvc.perform(post("/api/v1/join-requests/REQ_001/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value("REQ_001"))
                .andExpect(jsonPath("$.data.approvalStatus").value("Approved"));

        verify(branchJoinRequestService).approveJoinRequest(eq("REQ_001"), any());
    }

    @Test
    @DisplayName("POST /api/v1/join-requests/{requestId}/reject with valid reason should return 200 OK")
    void rejectJoinRequest_Success() throws Exception {
        String jsonPayload = """
                {
                    "rejectionReason": "Thông tin chi phái không chính xác với gia phả"
                }
                """;

        JoinRequestResponse rejectedResponse = JoinRequestResponse.builder()
                .requestId("REQ_001")
                .accountId("ACC_001")
                .clanId("CLAN_01")
                .fullName("Nguyễn Văn A")
                .approvalStatus(JoinRequestApprovalStatus.Rejected)
                .rejectionReason("Thông tin chi phái không chính xác với gia phả")
                .reviewedByAccountId("ACC_ADMIN")
                .reviewedAt(Instant.now())
                .build();

        when(branchJoinRequestService.rejectJoinRequest(eq("REQ_001"), any(RejectJoinRequest.class), any()))
                .thenReturn(rejectedResponse);

        mockMvc.perform(post("/api/v1/join-requests/REQ_001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value("REQ_001"))
                .andExpect(jsonPath("$.data.approvalStatus").value("Rejected"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Thông tin chi phái không chính xác với gia phả"));
    }

    @Test
    @DisplayName("POST /api/v1/join-requests/{requestId}/reject with blank reason should return 400 Bad Request")
    void rejectJoinRequest_BlankReason_Returns400() throws Exception {
        String jsonPayload = """
                {
                    "rejectionReason": "   "
                }
                """;

        mockMvc.perform(post("/api/v1/join-requests/REQ_001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}

