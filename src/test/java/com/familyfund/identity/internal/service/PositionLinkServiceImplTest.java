package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.api.CreateVirtualMemberCommand;
import com.familyfund.genealogy.api.GenealogyMemberCommandService;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.VirtualMemberDto;
import com.familyfund.identity.api.dto.CreateVirtualMemberRequest;
import com.familyfund.identity.api.dto.CreateVirtualMemberResponse;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

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
 * Unit tests for {@link PositionLinkServiceImpl#createVirtualMemberWithPositionLink(String, CreateVirtualMemberRequest)} (US-18).
 * The branch is passed separately because it comes from the request path
 * (POST /api/v1/branches/{branchId}/virtual-members).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PositionLinkServiceImplTest {

    private static final String CLAN = "CLAN_DEFAULT";
    private static final String BRANCH = "BR_CHI_2";
    private static final String ACCOUNT = "ACC_TRUONG_TOC";
    private static final String MEMBER = "MEMK4F8ZQ1M7XB3QP";
    private static final String POSITION_LINK_BASE = "http://localhost:3000/position-link";

    @Mock
    private InvitationCodeRepository invitationCodeRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private AccountRoleRepository accountRoleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private GenealogyMemberCommandService genealogyMemberCommandService;
    @Mock
    private GenealogyQueryService genealogyQueryService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Spy
    private InviteCodeGenerator inviteCodeGenerator =
            new InviteCodeGenerator("http://localhost:3000/join", POSITION_LINK_BASE);

    @InjectMocks
    private PositionLinkServiceImpl positionLinkService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACCOUNT));
        when(genealogyQueryService.getClanName(CLAN)).thenReturn(Optional.of("Dòng Họ Nguyễn"));
        when(genealogyMemberCommandService.createVirtualMember(any(CreateVirtualMemberCommand.class)))
                .thenReturn(virtualMember());
        when(invitationCodeRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(invitationCodeRepository.saveAndFlush(any(InvitationCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static VirtualMemberDto virtualMember() {
        return new VirtualMemberDto(
                MEMBER, "Nguyen Van An", LocalDate.of(1990, 5, 20), "Male", 3, "Living", null,
                BRANCH, "Chi 2 - Nhánh Giáp", CLAN, true, Instant.parse("2026-10-31T00:00:00Z"));
    }

    private static CreateVirtualMemberRequest request() {
        return CreateVirtualMemberRequest.builder()
                .fullName("  Nguyen Van An  ")
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .gender("Male")
                .generation(3)
                .build();
    }

    private InvitationCode capturedCode() {
        ArgumentCaptor<InvitationCode> captor = ArgumentCaptor.forClass(InvitationCode.class);
        verify(invitationCodeRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Happy path: placeholder position + one-time position link code")
    class Creation {

        @Test
        @DisplayName("Returns both the virtual profile and the code bound to it")
        void createsProfileAndCode() {
            CreateVirtualMemberResponse response = positionLinkService
                    .createVirtualMemberWithPositionLink(BRANCH, request());

            assertEquals(MEMBER, response.member().memberId());
            assertEquals(BRANCH, response.member().branchId());
            assertEquals("Chi 2 - Nhánh Giáp", response.member().branchName());
            assertEquals(CLAN, response.member().clanId());
            assertTrue(response.member().isPlaceholder());

            assertEquals(InviteType.PositionLinkCode, response.positionLinkCode().getInviteType());
            assertEquals(MEMBER, response.positionLinkCode().getTargetMemberId());
            assertEquals(BRANCH, response.positionLinkCode().getTargetBranchId());
            assertEquals(CLAN, response.positionLinkCode().getClanId());
            assertEquals("Dòng Họ Nguyễn", response.positionLinkCode().getClanName());
            assertEquals("Chi 2 - Nhánh Giáp", response.positionLinkCode().getTargetBranchName());
            assertEquals(ACCOUNT, response.positionLinkCode().getCreatedByAccountId());
            assertNotNull(response.positionLinkCode().getInviteCode());
            assertEquals(InviteCodeGenerator.CODE_LENGTH, response.positionLinkCode().getInviteCode().length());
        }

        @Test
        @DisplayName("The persisted code is one-time use: max_uses = 1, used_count = 0, active, position-link URL")
        void forcesOneTimeUse() {
            CreateVirtualMemberResponse response = positionLinkService
                    .createVirtualMemberWithPositionLink(BRANCH, request());

            InvitationCode stored = capturedCode();
            assertEquals(InviteType.PositionLinkCode, stored.getInviteType());
            assertEquals(1, stored.getMaxUses());
            assertEquals(0, stored.getUsedCount());
            assertTrue(stored.getIsActive());
            assertEquals(CLAN, stored.getClanId());
            assertEquals(BRANCH, stored.getTargetBranchId());
            assertEquals(MEMBER, stored.getTargetMemberId());
            assertEquals(ACCOUNT, stored.getCreatedByAccountId());
            assertTrue(stored.getShareableUrl().startsWith(POSITION_LINK_BASE + "?code="));
            assertTrue(stored.getShareableUrl().endsWith(response.positionLinkCode().getInviteCode()));
            assertEquals(stored.getInviteCode(), response.positionLinkCode().getInviteCode());
        }

        @Test
        @DisplayName("Code expires after the default 7 days when the client sends no expiry")
        void defaultExpiry_SevenDays() {
            positionLinkService.createVirtualMemberWithPositionLink(BRANCH, request());

            Instant expiresAt = capturedCode().getExpiresAt();
            assertTrue(expiresAt.isAfter(Instant.now().plus(6, ChronoUnit.DAYS)));
            assertTrue(expiresAt.isBefore(Instant.now().plus(8, ChronoUnit.DAYS)));
        }

        @Test
        @DisplayName("validityDays overrides the default validity")
        void validityDays_Override() {
            CreateVirtualMemberRequest payload = CreateVirtualMemberRequest.builder()
                    .fullName("Nguyen Van An").gender("Male").generation(3).validityDays(30)
                    .build();

            positionLinkService.createVirtualMemberWithPositionLink(BRANCH, payload);

            Instant expiresAt = capturedCode().getExpiresAt();
            assertTrue(expiresAt.isAfter(Instant.now().plus(29, ChronoUnit.DAYS)));
            assertTrue(expiresAt.isBefore(Instant.now().plus(31, ChronoUnit.DAYS)));
        }

        @Test
        @DisplayName("An explicit expiresAt wins over validityDays")
        void explicitExpiresAt_Wins() {
            Instant explicit = Instant.now().plus(2, ChronoUnit.HOURS);
            CreateVirtualMemberRequest payload = CreateVirtualMemberRequest.builder()
                    .fullName("Nguyen Van An").gender("Male").generation(3)
                    .expiresAt(explicit).validityDays(30)
                    .build();

            positionLinkService.createVirtualMemberWithPositionLink(BRANCH, payload);

            assertEquals(explicit, capturedCode().getExpiresAt());
        }

        @Test
        @DisplayName("Clan, trimmed branch id and blank lunar date are handed to the genealogy module")
        void passesCanonicalCommand() {
            ArgumentCaptor<CreateVirtualMemberCommand> captor = ArgumentCaptor.forClass(CreateVirtualMemberCommand.class);
            CreateVirtualMemberRequest payload = CreateVirtualMemberRequest.builder()
                    .fullName("Nguyen Van An").gender("Male").generation(3).deathDateLunar("   ")
                    .build();

            positionLinkService.createVirtualMemberWithPositionLink("  " + BRANCH + "  ", payload);

            verify(genealogyMemberCommandService).createVirtualMember(captor.capture());
            CreateVirtualMemberCommand command = captor.getValue();
            assertEquals(CLAN, command.clanId());
            assertEquals(BRANCH, command.branchId());
            assertEquals(3, command.generation());
            assertNull(command.deathDateLunar());
        }

        @Test
        @DisplayName("A colliding code is retried before it is persisted")
        void retriesOnCodeCollision() {
            when(invitationCodeRepository.existsByInviteCode(anyString())).thenReturn(true, false);

            positionLinkService.createVirtualMemberWithPositionLink(BRANCH, request());

            verify(invitationCodeRepository).saveAndFlush(any(InvitationCode.class));
        }
    }

    @Nested
    @DisplayName("Failures leave nothing behind")
    class Failures {

        @Test
        @DisplayName("Blank or oversized branch id is rejected (400) before anything is created")
        void invalidBranchId() {
            assertThrows(BadRequestException.class,
                    () -> positionLinkService.createVirtualMemberWithPositionLink("   ", request()));
            assertThrows(BadRequestException.class,
                    () -> positionLinkService.createVirtualMemberWithPositionLink(null, request()));
            assertThrows(BadRequestException.class,
                    () -> positionLinkService.createVirtualMemberWithPositionLink("B".repeat(21), request()));

            verify(genealogyMemberCommandService, never()).createVirtualMember(any());
            verify(invitationCodeRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("A caller without an account id cannot own the code (403)")
        void noAccount_Denied() {
            when(currentUserProvider.currentAccountId()).thenReturn(Optional.empty());

            assertThrows(AccessDeniedException.class,
                    () -> positionLinkService.createVirtualMemberWithPositionLink(BRANCH, request()));

            verify(genealogyMemberCommandService, never()).createVirtualMember(any());
            verify(invitationCodeRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("An expiry that is not in the future is rejected (400)")
        void pastExpiry_Rejected() {
            CreateVirtualMemberRequest payload = CreateVirtualMemberRequest.builder()
                    .fullName("Nguyen Van An").gender("Male").generation(3)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.SECONDS))
                    .build();

            assertThrows(BadRequestException.class,
                    () -> positionLinkService.createVirtualMemberWithPositionLink(BRANCH, payload));

            verify(genealogyMemberCommandService, never()).createVirtualMember(any());
            verify(invitationCodeRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Genealogy failures (404/403/400/409) propagate and no code is written")
        void genealogyFailure_Propagates() {
            when(genealogyMemberCommandService.createVirtualMember(any(CreateVirtualMemberCommand.class)))
                    .thenThrow(new ResourceNotFoundException("Branch", "branchId", BRANCH));

            assertThrows(ResourceNotFoundException.class,
                    () -> positionLinkService.createVirtualMemberWithPositionLink(BRANCH, request()));

            verify(invitationCodeRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Exhausted code collisions raise IllegalStateException and write nothing")
        void codeCollisions_GiveUp() {
            when(invitationCodeRepository.existsByInviteCode(anyString())).thenReturn(true);

            assertThrows(IllegalStateException.class,
                    () -> positionLinkService.createVirtualMemberWithPositionLink(BRANCH, request()));

            verify(invitationCodeRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("The tenant clan is never read from the request body")
        void tenantComesFromContext() {
            TenantContext.setClanId("CLAN_OTHER");

            positionLinkService.createVirtualMemberWithPositionLink(BRANCH, request());

            ArgumentCaptor<CreateVirtualMemberCommand> captor = ArgumentCaptor.forClass(CreateVirtualMemberCommand.class);
            verify(genealogyMemberCommandService).createVirtualMember(captor.capture());
            assertEquals("CLAN_OTHER", captor.getValue().clanId());
            assertEquals("CLAN_OTHER", capturedCode().getClanId());
            assertFalse(capturedCode().getClanId().equals(CLAN));
        }
    }
}
