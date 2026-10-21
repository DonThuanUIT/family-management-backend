package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.identity.api.dto.JoinRequestResponse;
import com.familyfund.identity.api.dto.SubmitJoinRequest;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.BranchJoinRequest;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import com.familyfund.identity.internal.entity.ProposalType;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.BranchJoinRequestRepository;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchJoinRequestServiceTest {

    private static final String CLAN_ID = "CLAN_01";
    private static final String INVITE_CODE = "8K9P2X7N";

    @Mock
    private InvitationCodeRepository invitationCodeRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private BranchJoinRequestRepository branchJoinRequestRepository;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private BranchJoinRequestServiceImpl branchJoinRequestService;

    private InvitationCode mockInvitation;
    private SubmitJoinRequest.SubmitJoinRequestBuilder requestBuilder;

    @BeforeEach
    void setUp() {
        mockInvitation = InvitationCode.builder()
                .inviteCode(INVITE_CODE)
                .clanId(CLAN_ID)
                .inviteType(InviteType.GeneralClanInvite)
                .isActive(true)
                .maxUses(100)
                .usedCount(5)
                .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .build();

        requestBuilder = SubmitJoinRequest.builder()
                .phoneNumber("0912345678")
                .password("Password123@")
                .email("test.user@familyfund.vn")
                .fullName("Nguyễn Văn An")
                .dateOfBirth(LocalDate.of(1995, 5, 20))
                .gender("Male")
                .proposalType(ProposalType.ExistingBranchLeaf)
                .targetBranchId("BR_ROOT")
                .proposedGeneration(4)
                .relationshipNote("Cháu nội cụ Nguyễn Văn B");
    }

    @Test
    @DisplayName("Submit join request for ExistingBranchLeaf should create pending account and request successfully")
    void submitJoinRequest_ExistingBranchLeaf_Success() {
        SubmitJoinRequest request = requestBuilder.build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(INVITE_CODE)).thenReturn(Optional.of(mockInvitation));
        when(userAccountRepository.existsByPhoneNumber("0912345678")).thenReturn(false);
        when(userAccountRepository.existsByEmail("test.user@familyfund.vn")).thenReturn(false);
        when(genealogyQueryService.existsBranch(CLAN_ID, "BR_ROOT")).thenReturn(true);
        when(genealogyQueryService.getBranchName(CLAN_ID, "BR_ROOT")).thenReturn(Optional.of("Chi Gốc"));
        when(passwordEncoder.encode("Password123@")).thenReturn("hashedPassword123");

        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(branchJoinRequestRepository.save(any(BranchJoinRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        JoinRequestResponse response = branchJoinRequestService.submitJoinRequest(INVITE_CODE, request);

        assertNotNull(response);
        assertEquals("Nguyễn Văn An", response.fullName());
        assertEquals("0912345678", response.phoneNumber());
        assertEquals("test.user@familyfund.vn", response.email());
        assertEquals(ProposalType.ExistingBranchLeaf, response.proposalType());
        assertEquals("BR_ROOT", response.targetBranchId());
        assertEquals("Chi Gốc", response.targetBranchName());
        assertEquals(4, response.proposedGeneration());
        assertEquals(JoinRequestApprovalStatus.Pending, response.approvalStatus());

        ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(accountCaptor.capture());
        UserAccount savedAccount = accountCaptor.getValue();
        assertEquals("0912345678", savedAccount.getPhoneNumber());
        assertEquals("hashedPassword123", savedAccount.getPasswordHash());
        assertEquals(AccountStatus.PendingApproval, savedAccount.getAccountStatus());
        assertNull(savedAccount.getMemberId());

        ArgumentCaptor<BranchJoinRequest> requestCaptor = ArgumentCaptor.forClass(BranchJoinRequest.class);
        verify(branchJoinRequestRepository).save(requestCaptor.capture());
        BranchJoinRequest savedRequest = requestCaptor.getValue();
        assertEquals(CLAN_ID, savedRequest.getClanId());
        assertEquals(INVITE_CODE, savedRequest.getInviteCode());
        assertEquals(JoinRequestApprovalStatus.Pending, savedRequest.getApprovalStatus());
        assertEquals(savedAccount, savedRequest.getUserAccount());
    }

    @Test
    @DisplayName("Submit join request for NewChildBranch should require proposedNewBranchName")
    void submitJoinRequest_NewChildBranch_Success() {
        SubmitJoinRequest request = requestBuilder
                .proposalType(ProposalType.NewChildBranch)
                .proposedNewBranchName("Chi Nhánh Con Mới")
                .build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(INVITE_CODE)).thenReturn(Optional.of(mockInvitation));
        when(userAccountRepository.existsByPhoneNumber("0912345678")).thenReturn(false);
        when(genealogyQueryService.existsBranch(CLAN_ID, "BR_ROOT")).thenReturn(true);
        when(genealogyQueryService.getBranchName(CLAN_ID, "BR_ROOT")).thenReturn(Optional.of("Chi Gốc"));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(branchJoinRequestRepository.save(any(BranchJoinRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        JoinRequestResponse response = branchJoinRequestService.submitJoinRequest(INVITE_CODE, request);

        assertNotNull(response);
        assertEquals(ProposalType.NewChildBranch, response.proposalType());
        assertEquals("Chi Nhánh Con Mới", response.proposedNewBranchName());
    }

    @Test
    @DisplayName("Submit join request for NewChildBranch without branch name should throw BadRequestException")
    void submitJoinRequest_NewChildBranch_MissingName_ThrowsBadRequest() {
        SubmitJoinRequest request = requestBuilder
                .proposalType(ProposalType.NewChildBranch)
                .proposedNewBranchName("   ")
                .build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(INVITE_CODE)).thenReturn(Optional.of(mockInvitation));
        when(userAccountRepository.existsByPhoneNumber("0912345678")).thenReturn(false);
        when(genealogyQueryService.existsBranch(CLAN_ID, "BR_ROOT")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> branchJoinRequestService.submitJoinRequest(INVITE_CODE, request));
        assertTrue(ex.getMessage().contains("Tên chi nhánh mới không được để trống"));
        verify(branchJoinRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit join request with duplicated phone number should throw BadRequestException")
    void submitJoinRequest_DuplicatePhone_ThrowsBadRequest() {
        SubmitJoinRequest request = requestBuilder.build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(INVITE_CODE)).thenReturn(Optional.of(mockInvitation));
        when(userAccountRepository.existsByPhoneNumber("0912345678")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> branchJoinRequestService.submitJoinRequest(INVITE_CODE, request));
        assertTrue(ex.getMessage().contains("Số điện thoại đã được đăng ký"));
        verify(branchJoinRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit join request with non-existent target branch should throw ResourceNotFoundException")
    void submitJoinRequest_BranchNotExists_ThrowsResourceNotFound() {
        SubmitJoinRequest request = requestBuilder.targetBranchId("UNKNOWN_BRANCH").build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(INVITE_CODE)).thenReturn(Optional.of(mockInvitation));
        when(userAccountRepository.existsByPhoneNumber("0912345678")).thenReturn(false);
        when(genealogyQueryService.existsBranch(CLAN_ID, "UNKNOWN_BRANCH")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> branchJoinRequestService.submitJoinRequest(INVITE_CODE, request));
        verify(branchJoinRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit join request with branch different from invitation targetBranchId should throw BadRequestException")
    void submitJoinRequest_BranchMismatch_ThrowsBadRequest() {
        mockInvitation.setTargetBranchId("BR_SPECIFIC");
        SubmitJoinRequest request = requestBuilder.targetBranchId("BR_ROOT").build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(INVITE_CODE)).thenReturn(Optional.of(mockInvitation));
        when(userAccountRepository.existsByPhoneNumber("0912345678")).thenReturn(false);
        when(genealogyQueryService.existsBranch(CLAN_ID, "BR_ROOT")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> branchJoinRequestService.submitJoinRequest(INVITE_CODE, request));
        assertTrue(ex.getMessage().contains("chỉ áp dụng cho nhánh mục tiêu"));
    }

    @Test
    @DisplayName("Submit join request with expired invite code should throw BadRequestException")
    void submitJoinRequest_ExpiredInvite_ThrowsBadRequest() {
        mockInvitation.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        SubmitJoinRequest request = requestBuilder.build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(INVITE_CODE)).thenReturn(Optional.of(mockInvitation));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> branchJoinRequestService.submitJoinRequest(INVITE_CODE, request));
        assertTrue(ex.getMessage().contains("hết hạn sử dụng"));
    }

    @Test
    @DisplayName("Submit join request with exceeded max uses should throw BadRequestException")
    void submitJoinRequest_ExceededMaxUses_ThrowsBadRequest() {
        mockInvitation.setMaxUses(5);
        mockInvitation.setUsedCount(5);
        SubmitJoinRequest request = requestBuilder.build();

        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(INVITE_CODE)).thenReturn(Optional.of(mockInvitation));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> branchJoinRequestService.submitJoinRequest(INVITE_CODE, request));
        assertTrue(ex.getMessage().contains("hết lượt sử dụng"));
    }
}
