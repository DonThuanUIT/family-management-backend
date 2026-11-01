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
import org.junit.jupiter.api.Nested;
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
 * Contextual Security Test suite for US-06: Phân hệ Đăng ký tham dự sự kiện.
 * Verifies contextual RBAC permissions on registration management and review.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationContextualSecurityTest")
class RegistrationContextualSecurityTest {

    private static final String CLAN_ID = "CLAN_01";
    private static final String FOREIGN_CLAN_ID = "CLAN_02";
    private static final String EVENT_ID = "EVT_TEST_01";
    private static final String MEMBER_ID = "MEM_HEAD_01";

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventPersonnelRepository eventPersonnelRepository;

    @InjectMocks
    private EventSecurity eventSecurity;

    private Event activeEvent;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        activeEvent = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .status(EventStatus.RegistrationOpen)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Registration Review Permissions (HeadOfOrganizer, CLAN_HEAD, ADMIN)")
    class ReviewPermissions {

        @Test
        @DisplayName("Global ADMIN should always be authorized to review registrations")
        void admin_authorized() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);

            assertTrue(eventSecurity.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }

        @Test
        @DisplayName("CLAN_HEAD should be authorized when event exists in clan")
        void clanHead_authorized() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
            when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);

            assertTrue(eventSecurity.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }

        @Test
        @DisplayName("CLAN_HEAD should be denied if event belongs to another clan")
        void clanHead_foreignClan_denied() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
            when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(false);

            assertFalse(eventSecurity.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }

        @Test
        @DisplayName("HeadOfOrganizer should be authorized to review registrations")
        void headOfOrganizer_authorized() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));

            EventPersonnel personnel = EventPersonnel.builder()
                    .eventId(EVENT_ID)
                    .memberId(MEMBER_ID)
                    .eventRole(EventRole.HeadOfOrganizer)
                    .isReadOnly(false)
                    .build();
            when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, MEMBER_ID))
                    .thenReturn(List.of(personnel));

            assertTrue(eventSecurity.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }

        @Test
        @DisplayName("OrganizerMember should be denied from reviewing registrations (HeadOfOrganizer required)")
        void organizerMember_deniedForReview() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));

            EventPersonnel personnel = EventPersonnel.builder()
                    .eventId(EVENT_ID)
                    .memberId(MEMBER_ID)
                    .eventRole(EventRole.OrganizerMember)
                    .isReadOnly(false)
                    .build();
            when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, MEMBER_ID))
                    .thenReturn(List.of(personnel));

            assertFalse(eventSecurity.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }

        @Test
        @DisplayName("ReadOnly personnel should be denied review authority even if role matches")
        void readOnlyPersonnel_denied() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));

            EventPersonnel personnel = EventPersonnel.builder()
                    .eventId(EVENT_ID)
                    .memberId(MEMBER_ID)
                    .eventRole(EventRole.HeadOfOrganizer)
                    .isReadOnly(true)
                    .build();
            when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, MEMBER_ID))
                    .thenReturn(List.of(personnel));

            assertFalse(eventSecurity.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }

        @Test
        @DisplayName("Review should be denied when event status is Closed")
        void closedEvent_denied() {
            activeEvent.setStatus(EventStatus.Closed);
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));

            assertFalse(eventSecurity.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }
    }

    @Nested
    @DisplayName("Registration Summary & List Permissions")
    class SummaryAndListPermissions {

        @Test
        @DisplayName("OrganizerMember and EventTreasurer should have access to summary")
        void organizerAndTreasurer_canViewSummary() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));

            EventPersonnel treasurer = EventPersonnel.builder()
                    .eventId(EVENT_ID)
                    .memberId(MEMBER_ID)
                    .eventRole(EventRole.EventTreasurer)
                    .isReadOnly(false)
                    .build();
            when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, MEMBER_ID))
                    .thenReturn(List.of(treasurer));

            assertTrue(eventSecurity.hasContextualRole(EVENT_ID, "HeadOfOrganizer", "OrganizerMember", "EventTreasurer"));
        }

        @Test
        @DisplayName("Regular member without assigned role cannot view restricted registration list")
        void regularMember_deniedList() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("REGULAR_MEM"));
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
            when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, "REGULAR_MEM"))
                    .thenReturn(List.of());

            assertFalse(eventSecurity.hasContextualRole(EVENT_ID, "HeadOfOrganizer", "OrganizerMember"));
        }

        @Test
        @DisplayName("Unauthenticated or unlinked account cannot access contextual permissions")
        void unlinkedAccount_denied() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.empty());

            assertFalse(eventSecurity.hasContextualRole(EVENT_ID, "HeadOfOrganizer", "OrganizerMember"));
        }
    }
}

