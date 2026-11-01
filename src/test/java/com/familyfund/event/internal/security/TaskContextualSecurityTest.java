package com.familyfund.event.internal.security;

import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Contextual Security Test suite for US-07: Phân hệ Công việc & tạm ứng.
 * Verifies that task creation and assignment are strictly restricted to
 * Ban tổ chức (HeadOfOrganizer, OrganizerMember), Clan Head, or Admin,
 * while preventing unauthorized roles, cross-clan access, and closed-event modifications.
 */
@ExtendWith(MockitoExtension.class)
class TaskContextualSecurityTest {

    private static final String CLAN_ID = "CLAN_TEST_01";
    private static final String FOREIGN_CLAN_ID = "CLAN_FOREIGN_02";
    private static final String EVENT_ID = "EVT_TEST_001";
    private static final String MEMBER_ID = "MEM_BTC_001";

    private static final String[] TASK_WRITE_ROLES = {"HeadOfOrganizer", "OrganizerMember"};

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventPersonnelRepository eventPersonnelRepository;

    @InjectMocks
    private EventSecurity eventSecurity;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Contextual RBAC: HeadOfOrganizer is authorized to create and assign tasks")
    void headOfOrganizer_AuthorizedForTasks() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));

        Event event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .status(EventStatus.Draft)
                .build();
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

        EventPersonnel personnel = EventPersonnel.builder()
                .eventId(EVENT_ID)
                .memberId(MEMBER_ID)
                .eventRole(EventRole.HeadOfOrganizer)
                .isReadOnly(false)
                .build();
        when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, MEMBER_ID))
                .thenReturn(List.of(personnel));

        assertTrue(eventSecurity.hasContextualRole(EVENT_ID, TASK_WRITE_ROLES));
    }

    @Test
    @DisplayName("Contextual RBAC: OrganizerMember is authorized to create and assign tasks")
    void organizerMember_AuthorizedForTasks() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));

        Event event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .status(EventStatus.Ongoing)
                .build();
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

        EventPersonnel personnel = EventPersonnel.builder()
                .eventId(EVENT_ID)
                .memberId(MEMBER_ID)
                .eventRole(EventRole.OrganizerMember)
                .isReadOnly(false)
                .build();
        when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, MEMBER_ID))
                .thenReturn(List.of(personnel));

        assertTrue(eventSecurity.hasContextualRole(EVENT_ID, TASK_WRITE_ROLES));
    }

    @Test
    @DisplayName("Contextual RBAC: EventTreasurer is denied from creating and assigning tasks (role separation)")
    void eventTreasurer_DeniedFromTaskManagement() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));

        Event event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .status(EventStatus.Ongoing)
                .build();
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

        EventPersonnel personnel = EventPersonnel.builder()
                .eventId(EVENT_ID)
                .memberId(MEMBER_ID)
                .eventRole(EventRole.EventTreasurer)
                .isReadOnly(false)
                .build();
        when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, MEMBER_ID))
                .thenReturn(List.of(personnel));

        assertFalse(eventSecurity.hasContextualRole(EVENT_ID, TASK_WRITE_ROLES));
    }

    @Test
    @DisplayName("Contextual RBAC: Member with read-only flag is denied")
    void readOnlyPersonnel_Denied() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));

        Event event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .status(EventStatus.Draft)
                .build();
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

        EventPersonnel personnel = EventPersonnel.builder()
                .eventId(EVENT_ID)
                .memberId(MEMBER_ID)
                .eventRole(EventRole.HeadOfOrganizer)
                .isReadOnly(true)
                .build();
        when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, MEMBER_ID))
                .thenReturn(List.of(personnel));

        assertFalse(eventSecurity.hasContextualRole(EVENT_ID, TASK_WRITE_ROLES));
    }

    @Test
    @DisplayName("Contextual RBAC: Regular member without event personnel assignment is denied")
    void regularMember_WithoutRole_Denied() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));

        Event event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .status(EventStatus.Draft)
                .build();
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));
        when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, MEMBER_ID))
                .thenReturn(List.of());

        assertFalse(eventSecurity.hasContextualRole(EVENT_ID, TASK_WRITE_ROLES));
    }

    @Test
    @DisplayName("Global RBAC: CLAN_HEAD is authorized for events belonging to own clan")
    void clanHead_AuthorizedForOwnClanEvents() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);

        assertTrue(eventSecurity.hasContextualRole(EVENT_ID, TASK_WRITE_ROLES));
    }

    @Test
    @DisplayName("Global RBAC: CLAN_HEAD is denied for events belonging to foreign clan (multi-tenant boundary)")
    void clanHead_DeniedForForeignClanEvents() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(false);

        assertFalse(eventSecurity.hasContextualRole(EVENT_ID, TASK_WRITE_ROLES));
    }

    @Test
    @DisplayName("Global RBAC: ADMIN has global bypass authorization")
    void admin_GloballyAuthorized() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);

        assertTrue(eventSecurity.hasContextualRole(EVENT_ID, TASK_WRITE_ROLES));
    }

    @Test
    @DisplayName("State Security: Closed event freezes all task creation and assignment")
    void closedEvent_FreezesModifications() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));

        Event event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .status(EventStatus.Closed)
                .build();
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

        assertFalse(eventSecurity.hasContextualRole(EVENT_ID, TASK_WRITE_ROLES));
    }

    @Test
    @DisplayName("Authentication Guard: Unlinked user without member_id is denied")
    void unlinkedUser_Denied() {
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.empty());

        assertFalse(eventSecurity.hasContextualRole(EVENT_ID, TASK_WRITE_ROLES));
    }

    @Test
    @DisplayName("Input Validation: Null or blank eventId returns false immediately")
    void blankEventId_Denied() {
        assertFalse(eventSecurity.hasContextualRole(null, TASK_WRITE_ROLES));
        assertFalse(eventSecurity.hasContextualRole("", TASK_WRITE_ROLES));
        assertFalse(eventSecurity.hasContextualRole("   ", TASK_WRITE_ROLES));
    }
}
