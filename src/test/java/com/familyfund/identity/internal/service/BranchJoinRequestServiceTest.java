package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.CreateOnboardingBranchCommand;
import com.familyfund.genealogy.api.CreateOnboardingMemberCommand;
import com.familyfund.genealogy.api.GenealogyMemberCommandService;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.identity.api.dto.JoinRequestResponse;
import com.familyfund.identity.api.dto.RejectJoinRequest;
import com.familyfund.identity.api.dto.SubmitJoinRequest;
import com.familyfund.identity.internal.entity.AccountRole;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.BranchJoinRequest;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import com.familyfund.identity.internal.entity.ProposalType;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.AccountRoleRepository;
import com.familyfund.identity.internal.repository.BranchJoinRequestRepository;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private GenealogyMemberCommandService genealogyMemberCommandService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private BranchJoinRequestServiceImpl branchJoinRequestService;

    private InvitationCode mockInvitation;
    private SubmitJoinRequest.SubmitJoinRequestBuilder requestBuilder;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);

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

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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

    // --- Task 5 Tests: Listing Join Requests ---

    @Test
    @DisplayName("getJoinRequests for CLAN_HEAD should query clan-wide")
    @SuppressWarnings("unchecked")
    void getJoinRequests_ClanHead_QueriesClanWide() {
        Authentication auth = mock(Authentication.class);
        when(auth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_CLAN_HEAD")));

        BranchJoinRequest req = BranchJoinRequest.builder()
                .requestId("REQ_01")
                .clanId(CLAN_ID)
                .targetBranchId("BR_ROOT")
                .fullName("Nguyễn Văn A")
                .approvalStatus(JoinRequestApprovalStatus.Pending)
                .build();

        Page<BranchJoinRequest> page = new PageImpl<>(List.of(req));
        Pageable pageable = PageRequest.of(0, 20);

        when(branchJoinRequestRepository.findWithAccountByClanIdAndApprovalStatus(CLAN_ID, JoinRequestApprovalStatus.Pending, pageable))
                .thenReturn(page);
        when(genealogyQueryService.getBranchName(CLAN_ID, "BR_ROOT")).thenReturn(Optional.of("Chi Gốc"));

        Page<JoinRequestResponse> result = branchJoinRequestService.getJoinRequests(null, null, pageable, auth);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("REQ_01", result.getContent().get(0).requestId());
        assertEquals("Chi Gốc", result.getContent().get(0).targetBranchName());
    }

    @Test
    @DisplayName("getJoinRequests for BRANCH_HEAD should query scoped to managed branches")
    @SuppressWarnings("unchecked")
    void getJoinRequests_BranchHead_QueriesScopedBranches() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ACC_BRANCH_HEAD");
        when(auth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD")));

        UserAccount branchHeadAccount = UserAccount.builder()
                .accountId("ACC_BRANCH_HEAD")
                .memberId("MEM_HEAD")
                .build();
        when(userAccountRepository.findById("ACC_BRANCH_HEAD")).thenReturn(Optional.of(branchHeadAccount));
        when(genealogyQueryService.getBranchIdsManagedByMember(CLAN_ID, "MEM_HEAD")).thenReturn(List.of("BR_01", "BR_01_01"));

        BranchJoinRequest req = BranchJoinRequest.builder()
                .requestId("REQ_02")
                .clanId(CLAN_ID)
                .targetBranchId("BR_01")
                .fullName("Nguyễn Văn B")
                .approvalStatus(JoinRequestApprovalStatus.Pending)
                .build();

        Page<BranchJoinRequest> page = new PageImpl<>(List.of(req));
        Pageable pageable = PageRequest.of(0, 20);

        when(branchJoinRequestRepository.findWithAccountByClanIdAndBranchIdsAndApprovalStatus(
                eq(CLAN_ID), eq(List.of("BR_01", "BR_01_01")), eq(JoinRequestApprovalStatus.Pending), eq(pageable)))
                .thenReturn(page);
        when(genealogyQueryService.getBranchName(CLAN_ID, "BR_01")).thenReturn(Optional.of("Chi 1"));

        Page<JoinRequestResponse> result = branchJoinRequestService.getJoinRequests(JoinRequestApprovalStatus.Pending, null, pageable, auth);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("REQ_02", result.getContent().get(0).requestId());
    }

    // --- Task 3 Tests: Approval & Rejection Processing ---

    @Test
    @DisplayName("approveJoinRequest for ExistingBranchLeaf should create member, activate user, and assign ROLE_MEMBER")
    @SuppressWarnings("unchecked")
    void approveJoinRequest_ExistingBranchLeaf_Success() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ACC_CLAN_HEAD");
        when(auth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_CLAN_HEAD")));

        UserAccount pendingAccount = UserAccount.builder()
                .accountId("ACC_USER_01")
                .accountStatus(AccountStatus.PendingApproval)
                .phoneNumber("0987654321")
                .build();

        BranchJoinRequest request = BranchJoinRequest.builder()
                .requestId("REQ_APP_01")
                .clanId(CLAN_ID)
                .userAccount(pendingAccount)
                .inviteCode(INVITE_CODE)
                .fullName("Trần Văn Tân")
                .dateOfBirth(LocalDate.of(1996, 4, 12))
                .gender("Male")
                .proposalType(ProposalType.ExistingBranchLeaf)
                .targetBranchId("BR_ROOT")
                .proposedGeneration(4)
                .approvalStatus(JoinRequestApprovalStatus.Pending)
                .build();

        when(branchJoinRequestRepository.findWithAccountByRequestIdAndClanId("REQ_APP_01", CLAN_ID))
                .thenReturn(Optional.of(request));
        when(genealogyMemberCommandService.createMemberForExistingBranch(any(CreateOnboardingMemberCommand.class)))
                .thenReturn("MEM_NEW_01");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRoleRepository.save(any(AccountRole.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(INVITE_CODE)).thenReturn(Optional.of(mockInvitation));
        when(branchJoinRequestRepository.save(any(BranchJoinRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(genealogyQueryService.getBranchName(CLAN_ID, "BR_ROOT")).thenReturn(Optional.of("Chi Gốc"));

        JoinRequestResponse response = branchJoinRequestService.approveJoinRequest("REQ_APP_01", auth);

        assertNotNull(response);
        assertEquals(JoinRequestApprovalStatus.Approved, response.approvalStatus());
        assertEquals("ACC_CLAN_HEAD", response.reviewedByAccountId());

        // Verify account activated
        assertEquals(AccountStatus.Active, pendingAccount.getAccountStatus());
        assertEquals("MEM_NEW_01", pendingAccount.getMemberId());

        // Verify invitation counter incremented
        assertEquals(6, mockInvitation.getUsedCount());

        // Verify ROLE_MEMBER assignment
        ArgumentCaptor<AccountRole> roleCaptor = ArgumentCaptor.forClass(AccountRole.class);
        verify(accountRoleRepository).save(roleCaptor.capture());
        assertEquals("ROLE_MEMBER", roleCaptor.getValue().getId().getRoleId());
        assertEquals("ACC_USER_01", roleCaptor.getValue().getId().getAccountId());
    }

    @Test
    @DisplayName("approveJoinRequest for NewChildBranch should call createChildBranchAndMember")
    @SuppressWarnings("unchecked")
    void approveJoinRequest_NewChildBranch_Success() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ACC_ADMIN");
        when(auth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        UserAccount pendingAccount = UserAccount.builder()
                .accountId("ACC_USER_02")
                .accountStatus(AccountStatus.PendingApproval)
                .phoneNumber("0987654322")
                .build();

        BranchJoinRequest request = BranchJoinRequest.builder()
                .requestId("REQ_APP_02")
                .clanId(CLAN_ID)
                .userAccount(pendingAccount)
                .inviteCode(INVITE_CODE)
                .fullName("Trần Thị Hoa")
                .dateOfBirth(LocalDate.of(1992, 1, 15))
                .gender("Female")
                .proposalType(ProposalType.NewChildBranch)
                .targetBranchId("BR_ROOT")
                .proposedNewBranchName("Chi Nhánh Nữ")
                .proposedGeneration(3)
                .approvalStatus(JoinRequestApprovalStatus.Pending)
                .build();

        when(branchJoinRequestRepository.findWithAccountByRequestIdAndClanId("REQ_APP_02", CLAN_ID))
                .thenReturn(Optional.of(request));
        when(genealogyMemberCommandService.createChildBranchAndMember(any(CreateOnboardingBranchCommand.class)))
                .thenReturn("MEM_NEW_02");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRoleRepository.save(any(AccountRole.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invitationCodeRepository.findByInviteCodeAndIsActiveTrue(INVITE_CODE)).thenReturn(Optional.of(mockInvitation));
        when(branchJoinRequestRepository.save(any(BranchJoinRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        JoinRequestResponse response = branchJoinRequestService.approveJoinRequest("REQ_APP_02", auth);

        assertNotNull(response);
        assertEquals(JoinRequestApprovalStatus.Approved, response.approvalStatus());
        verify(genealogyMemberCommandService).createChildBranchAndMember(any(CreateOnboardingBranchCommand.class));
    }

    @Test
    @DisplayName("approveJoinRequest on already approved request should throw BusinessRuleException")
    @SuppressWarnings("unchecked")
    void approveJoinRequest_AlreadyApproved_ThrowsBusinessRule() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ACC_ADMIN");

        BranchJoinRequest request = BranchJoinRequest.builder()
                .requestId("REQ_APP_03")
                .clanId(CLAN_ID)
                .approvalStatus(JoinRequestApprovalStatus.Approved)
                .build();

        when(branchJoinRequestRepository.findWithAccountByRequestIdAndClanId("REQ_APP_03", CLAN_ID))
                .thenReturn(Optional.of(request));

        assertThrows(BusinessRuleException.class,
                () -> branchJoinRequestService.approveJoinRequest("REQ_APP_03", auth));
    }

    @Test
    @DisplayName("approveJoinRequest by unauthorized Branch Head should throw BadRequestException")
    @SuppressWarnings("unchecked")
    void approveJoinRequest_UnauthorizedBranchHead_ThrowsBadRequest() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ACC_BRANCH_HEAD");
        when(auth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_BRANCH_HEAD")));

        BranchJoinRequest request = BranchJoinRequest.builder()
                .requestId("REQ_APP_04")
                .clanId(CLAN_ID)
                .targetBranchId("BR_OTHER")
                .approvalStatus(JoinRequestApprovalStatus.Pending)
                .build();

        UserAccount branchHeadAccount = UserAccount.builder()
                .accountId("ACC_BRANCH_HEAD")
                .memberId("MEM_HEAD")
                .build();

        when(branchJoinRequestRepository.findWithAccountByRequestIdAndClanId("REQ_APP_04", CLAN_ID))
                .thenReturn(Optional.of(request));
        when(userAccountRepository.findById("ACC_BRANCH_HEAD")).thenReturn(Optional.of(branchHeadAccount));
        when(genealogyQueryService.getBranchIdsManagedByMember(CLAN_ID, "MEM_HEAD")).thenReturn(List.of("BR_01"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> branchJoinRequestService.approveJoinRequest("REQ_APP_04", auth));
        assertTrue(ex.getMessage().contains("không có quyền phê duyệt"));
    }

    @Test
    @DisplayName("rejectJoinRequest should deactivate user account and record rejection reason")
    @SuppressWarnings("unchecked")
    void rejectJoinRequest_Success() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ACC_CLAN_HEAD");
        when(auth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_CLAN_HEAD")));

        UserAccount pendingAccount = UserAccount.builder()
                .accountId("ACC_USER_05")
                .accountStatus(AccountStatus.PendingApproval)
                .build();

        BranchJoinRequest request = BranchJoinRequest.builder()
                .requestId("REQ_REJ_01")
                .clanId(CLAN_ID)
                .userAccount(pendingAccount)
                .targetBranchId("BR_ROOT")
                .approvalStatus(JoinRequestApprovalStatus.Pending)
                .build();

        when(branchJoinRequestRepository.findWithAccountByRequestIdAndClanId("REQ_REJ_01", CLAN_ID))
                .thenReturn(Optional.of(request));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(branchJoinRequestRepository.save(any(BranchJoinRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(genealogyQueryService.getBranchName(CLAN_ID, "BR_ROOT")).thenReturn(Optional.of("Chi Gốc"));

        RejectJoinRequest rejectReq = new RejectJoinRequest("Thông tin đời thứ không trùng khớp gia phả gốc");
        JoinRequestResponse response = branchJoinRequestService.rejectJoinRequest("REQ_REJ_01", rejectReq, auth);

        assertNotNull(response);
        assertEquals(JoinRequestApprovalStatus.Rejected, response.approvalStatus());
        assertEquals("Thông tin đời thứ không trùng khớp gia phả gốc", response.rejectionReason());
        assertEquals(AccountStatus.Deactivated, pendingAccount.getAccountStatus());
    }
}
