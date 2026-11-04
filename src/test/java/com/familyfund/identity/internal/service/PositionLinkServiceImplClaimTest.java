package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.ClaimVirtualMemberCommand;
import com.familyfund.genealogy.api.GenealogyMemberCommandService;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.genealogy.api.dto.VirtualMemberDto;
import com.familyfund.identity.api.dto.ClaimPositionRequest;
import com.familyfund.identity.api.dto.ClaimPositionResponse;
import com.familyfund.identity.api.dto.PositionLinkPreviewResponse;
import com.familyfund.identity.internal.entity.AccountRole;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.AccountRoleRepository;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import com.familyfund.identity.internal.util.InviteCodeGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the redemption half of {@link PositionLinkServiceImpl}: {@code claimPosition} and
 * {@code getPositionLinkPreview} (US-19).
 *
 * <p>The feature is "register with a position link code": the registrant proves who they are with
 * {@code phoneNumber + password}, so the service either creates an {@code Active} account bound to the position or
 * activates and binds an existing {@code PendingApproval} account left behind by the US-17 join flow. No approval
 * step exists in this flow, which is exactly what these tests pin down.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PositionLinkServiceImplClaimTest {

    private static final String CLAN = "CLAN_DEFAULT";
    private static final String OTHER_CLAN = "CLAN_OTHER";
    private static final String BRANCH = "BR_CHI_2";
    private static final String MEMBER = "MEMK4F8ZQ1M7XB3QP";
    private static final String CODE = "8K9P2X7N";
    private static final String PHONE = "0912345678";
    private static final String RAW_PASSWORD = "secret123";
    private static final String HASHED_PASSWORD = "{bcrypt}hashed";

    @Mock
    private InvitationCodeRepository invitationCodeRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private AccountRoleRepository accountRoleRepository;
    @Mock
    private GenealogyMemberCommandService genealogyMemberCommandService;
    @Mock
    private GenealogyQueryService genealogyQueryService;
    @Mock
    private InviteCodeGenerator inviteCodeGenerator;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PositionLinkServiceImpl positionLinkService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN);

        when(invitationCodeRepository.findById(CODE)).thenReturn(Optional.of(activeCode()));
        when(invitationCodeRepository.save(any(InvitationCode.class))).thenAnswer(inv -> inv.getArgument(0));

        when(passwordEncoder.encode(anyString())).thenReturn(HASHED_PASSWORD);
        when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

        when(userAccountRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(userAccountRepository.findByMemberId(MEMBER)).thenReturn(Optional.empty());
        when(userAccountRepository.existsByEmail(anyString())).thenReturn(false);
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        when(genealogyMemberCommandService.claimVirtualMember(any(ClaimVirtualMemberCommand.class)))
                .thenReturn(claimedMember());
        when(genealogyQueryService.getClanName(CLAN)).thenReturn(Optional.of("Dòng Họ Nguyễn"));
        when(genealogyQueryService.getBranchName(CLAN, BRANCH)).thenReturn(Optional.of("Chi 2 - Nhánh Giáp"));
        when(genealogyQueryService.findMemberSummary(MEMBER))
                .thenReturn(Optional.of(new MemberSummaryDto(MEMBER, "Nguyen Van An", CLAN, BRANCH)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ fixtures

    private static InvitationCode activeCode() {
        return InvitationCode.builder()
                .inviteCode(CODE)
                .clanId(CLAN)
                .inviteType(InviteType.PositionLinkCode)
                .shareableUrl("http://localhost:3000/position-link?code=" + CODE)
                .targetBranchId(BRANCH)
                .targetMemberId(MEMBER)
                .maxUses(1)
                .usedCount(0)
                .expiresAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .isActive(true)
                .createdByAccountId("ACC_TRUONG_TOC")
                .build();
    }

    private static VirtualMemberDto claimedMember() {
        return new VirtualMemberDto(
                MEMBER, "Nguyen Van An", LocalDate.of(1990, 5, 20), "Male", 3, "Living", null,
                BRANCH, "Chi 2 - Nhánh Giáp", CLAN, false, Instant.parse("2026-10-31T00:00:00Z"));
    }

    private static ClaimPositionRequest registration() {
        return ClaimPositionRequest.builder()
                .phoneNumber("  " + PHONE + "  ")
                .password(RAW_PASSWORD)
                .email("  AN@Example.COM  ")
                .build();
    }

    private static UserAccount pendingAccount() {
        return UserAccount.builder()
                .accountId("ACC_PENDING_0001")
                .phoneNumber(PHONE)
                .email("an@example.com")
                .passwordHash(HASHED_PASSWORD)
                .accountStatus(AccountStatus.PendingApproval)
                .build();
    }

    private UserAccount capturedSavedAccount() {
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        return captor.getValue();
    }

    private InvitationCode capturedSavedCode() {
        ArgumentCaptor<InvitationCode> captor = ArgumentCaptor.forClass(InvitationCode.class);
        verify(invitationCodeRepository).save(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------ registration + join

    @Nested
    @DisplayName("Registering with a valid position link code joins the branch immediately")
    class Registration {

        @Test
        @DisplayName("An unknown phone number gets an Active account bound to the position - no approval step")
        void newRegistrant_JoinsImmediately() {
            ClaimPositionResponse response = positionLinkService.claimPosition(CODE, registration());

            UserAccount saved = capturedSavedAccount();
            assertEquals(AccountStatus.Active, saved.getAccountStatus());
            assertEquals(MEMBER, saved.getMemberId());
            assertEquals(PHONE, saved.getPhoneNumber());
            assertEquals("an@example.com", saved.getEmail());
            assertEquals(HASHED_PASSWORD, saved.getPasswordHash());
            assertTrue(saved.getAccountId().startsWith("ACC_"));
            assertTrue(saved.getAccountId().length() <= 20);

            assertEquals(AccountStatus.Active.name(), response.accountStatus());
            assertEquals(MEMBER, response.memberId());
            assertEquals(BRANCH, response.branchId());
            assertEquals("Chi 2 - Nhánh Giáp", response.branchName());
            assertEquals(CLAN, response.clanId());
            assertEquals("Dòng Họ Nguyễn", response.clanName());
            assertEquals(CODE, response.inviteCode());
            assertEquals(Set.of(SystemRoles.MEMBER), response.roles());
            assertNotNull(response.linkedAt());
            assertEquals("Nguyen Van An", response.fullName());
            assertEquals(3, response.generation());
        }

        @Test
        @DisplayName("The claimant joins the branch recorded on the code, never the tenant of the request")
        void joinsTheBranchOfTheCode() {
            TenantContext.setClanId(OTHER_CLAN);

            positionLinkService.claimPosition(CODE, registration());

            ArgumentCaptor<ClaimVirtualMemberCommand> captor =
                    ArgumentCaptor.forClass(ClaimVirtualMemberCommand.class);
            verify(genealogyMemberCommandService).claimVirtualMember(captor.capture());
            assertEquals(CLAN, captor.getValue().clanId());
            assertEquals(MEMBER, captor.getValue().memberId());
            assertFalse(captor.getValue().clanId().equals(OTHER_CLAN));
        }

        @Test
        @DisplayName("The code is spent and ROLE_MEMBER is granted to the new account")
        void consumesCodeAndGrantsMemberRole() {
            positionLinkService.claimPosition(CODE, registration());

            InvitationCode stored = capturedSavedCode();
            assertEquals(1, stored.getUsedCount());
            assertFalse(stored.getIsActive());

            ArgumentCaptor<AccountRole> roleCaptor = ArgumentCaptor.forClass(AccountRole.class);
            verify(accountRoleRepository).save(roleCaptor.capture());
            assertEquals(SystemRoles.MEMBER, roleCaptor.getValue().getId().getRoleId());
            assertEquals(capturedSavedAccount().getAccountId(), roleCaptor.getValue().getId().getAccountId());
        }

        @Test
        @DisplayName("The profile is claimed through the genealogy contract (placeholder -> real profile)")
        void claimsTheVirtualMember() {
            positionLinkService.claimPosition(CODE, registration());

            verify(genealogyMemberCommandService).claimVirtualMember(any(ClaimVirtualMemberCommand.class));
        }

        @Test
        @DisplayName("An existing PendingApproval account is activated and bound instead of duplicated")
        void pendingAccount_IsActivated() {
            UserAccount pending = pendingAccount();
            when(userAccountRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(pending));

            ClaimPositionResponse response = positionLinkService.claimPosition(CODE, registration());

            assertEquals("ACC_PENDING_0001", response.accountId());
            assertEquals(MEMBER, pending.getMemberId());
            assertEquals(AccountStatus.Active, pending.getAccountStatus());
            assertEquals(AccountStatus.Active.name(), response.accountStatus());
            // The stored profile already had an email; claiming must not rewrite it from the payload.
            assertEquals("an@example.com", response.email());
        }

        @Test
        @DisplayName("ROLE_MEMBER is not inserted twice for an account that already holds it")
        void existingMemberRole_IsNotDuplicated() {
            UserAccount pending = pendingAccount();
            when(userAccountRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(pending));
            when(accountRoleRepository.existsByIdAccountIdAndIdRoleId("ACC_PENDING_0001", SystemRoles.MEMBER))
                    .thenReturn(true);

            positionLinkService.claimPosition(CODE, registration());

            verify(accountRoleRepository, never()).save(any(AccountRole.class));
        }

        @Test
        @DisplayName("A registration without an email is accepted and stores null")
        void registrationWithoutEmail() {
            ClaimPositionRequest payload = ClaimPositionRequest.builder()
                    .phoneNumber(PHONE).password(RAW_PASSWORD).build();

            ClaimPositionResponse response = positionLinkService.claimPosition(CODE, payload);

            assertNull(capturedSavedAccount().getEmail());
            assertNull(response.email());
        }
    }

    // ------------------------------------------------------------------ preview

    @Nested
    @DisplayName("Previewing the position of a code")
    class Preview {

        @Test
        @DisplayName("Returns clan, branch and the profile the code belongs to")
        void returnsPosition() {
            PositionLinkPreviewResponse preview = positionLinkService.getPositionLinkPreview("  " + CODE + "  ");

            assertEquals(CODE, preview.inviteCode());
            assertEquals(CLAN, preview.clanId());
            assertEquals("Dòng Họ Nguyễn", preview.clanName());
            assertEquals(BRANCH, preview.branchId());
            assertEquals("Chi 2 - Nhánh Giáp", preview.branchName());
            assertEquals(MEMBER, preview.memberId());
            assertEquals("Nguyen Van An", preview.fullName());
            assertNotNull(preview.expiresAt());
        }

        @Test
        @DisplayName("A general clan invite is not previewable as a position link (404)")
        void generalInvite_NotFound() {
            InvitationCode general = activeCode();
            general.setInviteType(InviteType.GeneralClanInvite);
            general.setTargetMemberId(null);
            when(invitationCodeRepository.findById(CODE)).thenReturn(Optional.of(general));

            assertThrows(ResourceNotFoundException.class,
                    () -> positionLinkService.getPositionLinkPreview(CODE));
        }
    }

    // ------------------------------------------------------------------ failures

    @Nested
    @DisplayName("A rejected claim writes nothing")
    class Failures {

        private void assertNothingWritten() {
            verify(genealogyMemberCommandService, never()).claimVirtualMember(any());
            verify(userAccountRepository, never()).save(any());
            verify(accountRoleRepository, never()).save(any());
            verify(invitationCodeRepository, never()).save(any());
        }

        /** The claim was attempted (genealogy refused it) but no account, role or code row was written. */
        private void assertNothingPersistedByAFailedClaim() {
            verify(genealogyMemberCommandService).claimVirtualMember(any());
            verify(userAccountRepository, never()).save(any());
            verify(accountRoleRepository, never()).save(any());
            verify(invitationCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Blank / oversized code (400)")
        void invalidCode() {
            assertThrows(BadRequestException.class, () -> positionLinkService.claimPosition("   ", registration()));
            assertThrows(BadRequestException.class, () -> positionLinkService.claimPosition(null, registration()));
            assertThrows(BadRequestException.class,
                    () -> positionLinkService.claimPosition("C".repeat(31), registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("Unknown code (404)")
        void unknownCode() {
            when(invitationCodeRepository.findById("NOPE0000")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> positionLinkService.claimPosition("NOPE0000", registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("A revoked (already used) code (400)")
        void inactiveCode() {
            InvitationCode revoked = activeCode();
            revoked.setIsActive(false);
            revoked.setUsedCount(1);
            when(invitationCodeRepository.findById(CODE)).thenReturn(Optional.of(revoked));

            assertThrows(BadRequestException.class, () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("An expired code (400)")
        void expiredCode() {
            InvitationCode expired = activeCode();
            expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
            when(invitationCodeRepository.findById(CODE)).thenReturn(Optional.of(expired));

            assertThrows(BadRequestException.class, () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("A code whose uses are exhausted (400)")
        void exhaustedUses() {
            InvitationCode used = activeCode();
            used.setUsedCount(1);
            when(invitationCodeRepository.findById(CODE)).thenReturn(Optional.of(used));

            assertThrows(BadRequestException.class, () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("A code that is not a position link (404)")
        void wrongInviteType() {
            InvitationCode general = activeCode();
            general.setInviteType(InviteType.GeneralClanInvite);
            when(invitationCodeRepository.findById(CODE)).thenReturn(Optional.of(general));

            assertThrows(ResourceNotFoundException.class,
                    () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("A position link without a target position (400)")
        void codeWithoutTargetMember() {
            InvitationCode broken = activeCode();
            broken.setTargetMemberId("   ");
            when(invitationCodeRepository.findById(CODE)).thenReturn(Optional.of(broken));

            assertThrows(BadRequestException.class, () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("Wrong password for an existing phone number (400)")
        void wrongPassword() {
            when(userAccountRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(pendingAccount()));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(false);

            assertThrows(BadRequestException.class, () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("A deactivated or suspended account cannot register a position (403)")
        void deactivatedAccount() {
            UserAccount deactivated = pendingAccount();
            deactivated.setAccountStatus(AccountStatus.Deactivated);
            when(userAccountRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(deactivated));

            assertThrows(AccessDeniedException.class,
                    () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("An account that already owns a profile cannot take a second one (409)")
        void accountAlreadyLinked() {
            UserAccount linked = pendingAccount();
            linked.setMemberId("MEM_OTHER");
            linked.setAccountStatus(AccountStatus.Active);
            when(userAccountRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(linked));

            assertThrows(DuplicateResourceException.class,
                    () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("A position already bound to another account is never re-bound (409)")
        void positionAlreadyBound() {
            when(userAccountRepository.findByMemberId(MEMBER))
                    .thenReturn(Optional.of(UserAccount.builder().accountId("ACC_OTHER").build()));

            assertThrows(DuplicateResourceException.class,
                    () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("An email that is already registered is rejected before the account is created (409)")
        void duplicateEmail() {
            when(userAccountRepository.existsByEmail("an@example.com")).thenReturn(true);

            assertThrows(DuplicateResourceException.class,
                    () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingWritten();
        }

        @Test
        @DisplayName("A position claimed meanwhile by someone else propagates 409 and writes nothing")
        void positionClaimedMeanwhile() {
            when(genealogyMemberCommandService.claimVirtualMember(any(ClaimVirtualMemberCommand.class)))
                    .thenThrow(new DuplicateResourceException("Vị trí phả hệ đã được nhận trước đó"));

            assertThrows(DuplicateResourceException.class,
                    () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingPersistedByAFailedClaim();
        }

        @Test
        @DisplayName("A missing placeholder profile propagates 404 and writes nothing")
        void positionMissing() {
            when(genealogyMemberCommandService.claimVirtualMember(any(ClaimVirtualMemberCommand.class)))
                    .thenThrow(new ResourceNotFoundException("Hồ sơ phả hệ", "memberId", MEMBER));

            assertThrows(ResourceNotFoundException.class,
                    () -> positionLinkService.claimPosition(CODE, registration()));
            assertNothingPersistedByAFailedClaim();
        }

        @Test
        @DisplayName("A blank phone number or password is rejected before any lookup (400)")
        void blankCredentials() {
            assertThrows(BadRequestException.class, () -> positionLinkService.claimPosition(CODE,
                    ClaimPositionRequest.builder().phoneNumber("  ").password(RAW_PASSWORD).build()));
            assertThrows(BadRequestException.class, () -> positionLinkService.claimPosition(CODE,
                    ClaimPositionRequest.builder().phoneNumber(PHONE).password("   ").build()));
            assertThrows(BadRequestException.class, () -> positionLinkService.claimPosition(CODE, null));
            assertNothingWritten();
        }
    }
}