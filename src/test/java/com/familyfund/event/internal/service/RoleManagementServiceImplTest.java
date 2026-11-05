package com.familyfund.event.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.AssignableRoleCode;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.RoleScopeType;
import com.familyfund.event.api.event.RoleChangedDomainEvent;
import com.familyfund.event.internal.dto.request.RoleAssignmentRequest;
import com.familyfund.event.internal.dto.request.RoleRevocationRequest;
import com.familyfund.event.internal.dto.response.AuditLogResponse;
import com.familyfund.event.internal.dto.response.RoleAssignmentResponse;
import com.familyfund.event.internal.dto.response.RoleManagementOverviewResponse;
import com.familyfund.core.audit.entity.AuditLog;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.entity.EventPersonnelId;
import com.familyfund.event.internal.repository.AuditLogRepository;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.util.AuditIdGenerator;
import com.familyfund.genealogy.api.GenealogyMemberCommandService;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.BranchSummaryDto;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceImplTest {

    private static final String CLAN_ID = "CLAN_TEST";
    private static final String ACTOR_ACCOUNT_ID = "ACC_CLAN_HEAD";
    private static final String MEMBER_ID = "MEM_001";
    private static final String ACCOUNT_ID = "ACC_001";
    private static final String BRANCH_ID = "BR_CHI_1";
    private static final String EVENT_ID = "EVT_001";

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventPersonnelRepository eventPersonnelRepository;

    @Mock
    private AuditIdGenerator auditIdGenerator;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private GenealogyMemberCommandService genealogyMemberCommandService;

    @Mock
    private IdentityRoleCommandService identityRoleCommandService;

    @InjectMocks
    private RoleManagementServiceImpl roleManagementService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        lenient().when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        lenient().when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACTOR_ACCOUNT_ID));
        lenient().when(auditIdGenerator.generate()).thenReturn("AUD_TEST_123");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @DisplayName("Should throw AccessDeniedException when caller is not CLAN_HEAD or ADMIN")
        void nonAuthorizedCaller_ThrowsAccessDenied() {
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);

            RoleAssignmentRequest request = new RoleAssignmentRequest(
                    MEMBER_ID, AssignableRoleCode.ROLE_BRANCH_HEAD, RoleScopeType.BRANCH, BRANCH_ID, null
            );

            assertThrows(AccessDeniedException.class, () -> roleManagementService.assignRole(request));
        }
    }

    @Nested
    @DisplayName("Branch Head Assignment & Revocation Tests")
    class BranchHeadTests {

        @Test
        @DisplayName("Assign ROLE_BRANCH_HEAD to member with active account - Success")
        void assignBranchHead_Success() {
            RoleAssignmentRequest request = new RoleAssignmentRequest(
                    MEMBER_ID, AssignableRoleCode.ROLE_BRANCH_HEAD, RoleScopeType.BRANCH, BRANCH_ID, "Bổ nhiệm trưởng chi 1"
            );

            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, BRANCH_ID)));
            when(genealogyQueryService.findBranchByIdAndClanId(BRANCH_ID, CLAN_ID))
                    .thenReturn(Optional.of(new BranchSummaryDto(BRANCH_ID, CLAN_ID, "Chi 1", 2, null, null)));
            when(identityRoleCommandService.requireActiveAccountByMemberId(MEMBER_ID, CLAN_ID))
                    .thenReturn(new AccountSummaryDto(ACCOUNT_ID, MEMBER_ID, "0901234567", "a@gtvn.vn", "Active", Set.of("ROLE_MEMBER")));
            when(genealogyMemberCommandService.assignBranchRepresentative(BRANCH_ID, CLAN_ID, MEMBER_ID))
                    .thenReturn(Optional.empty());

            RoleAssignmentResponse response = roleManagementService.assignRole(request);

            assertNotNull(response);
            assertEquals("AUD_TEST_123", response.auditId());
            assertEquals("ASSIGN", response.actionType());
            assertEquals("ROLE_BRANCH_HEAD", response.roleCode());
            assertEquals("Nguyễn Văn A", response.targetMemberName());
            assertEquals(ACCOUNT_ID, response.targetAccountId());

            verify(identityRoleCommandService).assignRoleToAccount(ACCOUNT_ID, "ROLE_BRANCH_HEAD");
            verify(auditLogRepository).save(any(AuditLog.class));

            ArgumentCaptor<RoleChangedDomainEvent> eventCaptor = ArgumentCaptor.forClass(RoleChangedDomainEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            RoleChangedDomainEvent captured = eventCaptor.getValue();
            assertEquals("AUD_TEST_123", captured.auditId());
            assertEquals(MEMBER_ID, captured.targetMemberId());
            assertEquals(ACCOUNT_ID, captured.targetAccountId());
        }

        @Test
        @DisplayName("Assign ROLE_BRANCH_HEAD replaces old head and revokes old role if no other branches")
        void assignBranchHead_ReplacesOldHead_RevokesOldRole() {
            String oldMemberId = "MEM_OLD";
            String oldAccountId = "ACC_OLD";

            RoleAssignmentRequest request = new RoleAssignmentRequest(
                    MEMBER_ID, AssignableRoleCode.ROLE_BRANCH_HEAD, RoleScopeType.BRANCH, BRANCH_ID, "Thay thế trưởng chi cũ"
            );

            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, BRANCH_ID)));
            when(genealogyQueryService.findBranchByIdAndClanId(BRANCH_ID, CLAN_ID))
                    .thenReturn(Optional.of(new BranchSummaryDto(BRANCH_ID, CLAN_ID, "Chi 1", 2, oldMemberId, "Cụ Cũ")));
            when(identityRoleCommandService.requireActiveAccountByMemberId(MEMBER_ID, CLAN_ID))
                    .thenReturn(new AccountSummaryDto(ACCOUNT_ID, MEMBER_ID, "0901234567", "a@gtvn.vn", "Active", Set.of("ROLE_MEMBER")));
            when(genealogyMemberCommandService.assignBranchRepresentative(BRANCH_ID, CLAN_ID, MEMBER_ID))
                    .thenReturn(Optional.of(oldMemberId));
            when(genealogyQueryService.isMemberRepresentativeOfAnyBranch(oldMemberId, CLAN_ID))
                    .thenReturn(false);
            when(identityRoleCommandService.findAccountByMemberId(oldMemberId))
                    .thenReturn(Optional.of(new AccountSummaryDto(oldAccountId, oldMemberId, "0909999999", "old@gtvn.vn", "Active", Set.of("ROLE_BRANCH_HEAD"))));

            RoleAssignmentResponse response = roleManagementService.assignRole(request);

            assertNotNull(response);
            verify(identityRoleCommandService).revokeRoleFromAccount(oldAccountId, "ROLE_BRANCH_HEAD");
            verify(identityRoleCommandService).assignRoleToAccount(ACCOUNT_ID, "ROLE_BRANCH_HEAD");
        }

        @Test
        @DisplayName("Revoke ROLE_BRANCH_HEAD - Success and removes role if no other branches")
        void revokeBranchHead_Success() {
            RoleRevocationRequest request = new RoleRevocationRequest(
                    MEMBER_ID, AssignableRoleCode.ROLE_BRANCH_HEAD, RoleScopeType.BRANCH, BRANCH_ID, "Thôi chức vụ"
            );

            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, BRANCH_ID)));
            when(genealogyQueryService.findBranchByIdAndClanId(BRANCH_ID, CLAN_ID))
                    .thenReturn(Optional.of(new BranchSummaryDto(BRANCH_ID, CLAN_ID, "Chi 1", 2, MEMBER_ID, "Nguyễn Văn A")));
            when(genealogyQueryService.isMemberRepresentativeOfAnyBranch(MEMBER_ID, CLAN_ID))
                    .thenReturn(false);
            when(identityRoleCommandService.findAccountByMemberId(MEMBER_ID))
                    .thenReturn(Optional.of(new AccountSummaryDto(ACCOUNT_ID, MEMBER_ID, "0901234567", "a@gtvn.vn", "Active", Set.of("ROLE_BRANCH_HEAD"))));

            RoleAssignmentResponse response = roleManagementService.revokeRole(request);

            assertNotNull(response);
            assertEquals("REVOKE", response.actionType());
            verify(genealogyMemberCommandService).revokeBranchRepresentative(BRANCH_ID, CLAN_ID, MEMBER_ID);
            verify(identityRoleCommandService).revokeRoleFromAccount(ACCOUNT_ID, "ROLE_BRANCH_HEAD");
            verify(auditLogRepository).save(any(AuditLog.class));
            verify(eventPublisher).publishEvent(any(RoleChangedDomainEvent.class));
        }
    }

    @Nested
    @DisplayName("Event Personnel Assignment & Revocation Tests")
    class EventPersonnelTests {

        @Test
        @DisplayName("Assign HeadOfOrganizer to event - Success")
        void assignHeadOfOrganizer_Success() {
            RoleAssignmentRequest request = new RoleAssignmentRequest(
                    MEMBER_ID, AssignableRoleCode.HeadOfOrganizer, RoleScopeType.EVENT, EVENT_ID, "Trưởng BTC Giỗ Tổ"
            );

            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, BRANCH_ID)));
            Event mockEvent = Event.builder().eventId(EVENT_ID).eventName("Đại Lễ Giỗ Tổ").status(EventStatus.RegistrationOpen).build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(mockEvent));
            when(eventPersonnelRepository.findById(any(EventPersonnelId.class))).thenReturn(Optional.empty());
            when(identityRoleCommandService.findAccountByMemberId(MEMBER_ID))
                    .thenReturn(Optional.of(new AccountSummaryDto(ACCOUNT_ID, MEMBER_ID, "0901234567", "a@gtvn.vn", "Active", Set.of("ROLE_MEMBER"))));

            RoleAssignmentResponse response = roleManagementService.assignRole(request);

            assertNotNull(response);
            assertEquals("HeadOfOrganizer", response.roleCode());
            assertEquals("Đại Lễ Giỗ Tổ", response.scopeName());
            verify(eventPersonnelRepository).save(any(EventPersonnel.class));
            verify(auditLogRepository).save(any(AuditLog.class));
            verify(eventPublisher).publishEvent(any(RoleChangedDomainEvent.class));
        }

        @Test
        @DisplayName("Assign role throws BusinessRuleException if Event is Closed")
        void assignRole_EventClosed_ThrowsException() {
            RoleAssignmentRequest request = new RoleAssignmentRequest(
                    MEMBER_ID, AssignableRoleCode.EventTreasurer, RoleScopeType.EVENT, EVENT_ID, "Thủ quỹ"
            );

            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, BRANCH_ID)));
            Event closedEvent = Event.builder().eventId(EVENT_ID).eventName("Sự kiện cũ").status(EventStatus.Closed).build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(closedEvent));

            assertThrows(BusinessRuleException.class, () -> roleManagementService.assignRole(request));
            verifyNoInteractions(eventPersonnelRepository);
        }

        @Test
        @DisplayName("Revoke EventTreasurer - Success")
        void revokeEventTreasurer_Success() {
            RoleRevocationRequest request = new RoleRevocationRequest(
                    MEMBER_ID, AssignableRoleCode.EventTreasurer, RoleScopeType.EVENT, EVENT_ID, "Miễn nhiệm thủ quỹ"
            );

            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, BRANCH_ID)));
            Event mockEvent = Event.builder().eventId(EVENT_ID).eventName("Đại Lễ Giỗ Tổ").status(EventStatus.RegistrationOpen).build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(mockEvent));

            RoleAssignmentResponse response = roleManagementService.revokeRole(request);

            assertNotNull(response);
            assertEquals("REVOKE", response.actionType());
            verify(eventPersonnelRepository).deleteById(new EventPersonnelId(EVENT_ID, MEMBER_ID, EventRole.EventTreasurer));
            verify(auditLogRepository).save(any(AuditLog.class));
            verify(eventPublisher).publishEvent(any(RoleChangedDomainEvent.class));
        }
    }

    @Nested
    @DisplayName("Audit Log & Overview Queries")
    class QueryTests {

        @Test
        @DisplayName("getAuditLogs returns mapped responses")
        void getAuditLogs_Success() {
            AuditLog log1 = AuditLog.builder()
                    .auditId("AUD_1")
                    .clanId(CLAN_ID)
                    .actorAccountId(ACTOR_ACCOUNT_ID)
                    .targetMemberId(MEMBER_ID)
                    .targetAccountId(ACCOUNT_ID)
                    .actionType("ASSIGN")
                    .roleCode("ROLE_BRANCH_HEAD")
                    .scopeType("BRANCH")
                    .scopeId(BRANCH_ID)
                    .scopeName("Chi 1")
                    .description("Bổ nhiệm")
                    .createdAt(Instant.now())
                    .build();

            when(auditLogRepository.findByClanIdOrderByCreatedAtDesc(CLAN_ID)).thenReturn(List.of(log1));
            when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                    .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, BRANCH_ID)));

            List<AuditLogResponse> logs = roleManagementService.getAuditLogs(null, null, null);

            assertEquals(1, logs.size());
            assertEquals("AUD_1", logs.get(0).auditId());
            assertEquals("Nguyễn Văn A", logs.get(0).targetFullName());
        }

        @Test
        @DisplayName("getRoleManagementOverview returns branches and member accounts")
        void getRoleManagementOverview_Success() {
            when(genealogyQueryService.findAllBranchesByClanId(CLAN_ID)).thenReturn(List.of(
                    new BranchSummaryDto(BRANCH_ID, CLAN_ID, "Chi 1", 2, MEMBER_ID, "Nguyễn Văn A")
            ));
            when(genealogyQueryService.findAllAliveMembersByClanId(CLAN_ID)).thenReturn(List.of(
                    new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, BRANCH_ID)
            ));
            when(identityRoleCommandService.findAccountByMemberId(MEMBER_ID)).thenReturn(
                    Optional.of(new AccountSummaryDto(ACCOUNT_ID, MEMBER_ID, "0901234567", "a@gtvn.vn", "Active", Set.of("ROLE_BRANCH_HEAD")))
            );
            when(identityRoleCommandService.findAccountsByMemberIds(List.of(MEMBER_ID))).thenReturn(
                    Map.of(MEMBER_ID, new AccountSummaryDto(ACCOUNT_ID, MEMBER_ID, "0901234567", "a@gtvn.vn", "Active", Set.of("ROLE_BRANCH_HEAD")))
            );

            RoleManagementOverviewResponse overview = roleManagementService.getRoleManagementOverview();

            assertNotNull(overview);
            assertEquals(CLAN_ID, overview.clanId());
            assertEquals(1, overview.branches().size());
            assertEquals(1, overview.members().size());
            assertTrue(overview.members().get(0).hasActiveAccount());
        }
    }
}
