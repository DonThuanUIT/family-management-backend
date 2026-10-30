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

@ExtendWith(MockitoExtension.class)
class EventSecurityTest {

    private static final String CLAN_ID = "CLAN_TEST";
    private static final String EVENT_ID = "EVT_001";
    private static final String MEMBER_ID = "MEM_001";

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventPersonnelRepository eventPersonnelRepository;

    @InjectMocks
    private EventSecurity security;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Admin and Clan Head global bypass")
    class GlobalRoles {

        @Test
        @DisplayName("Admin always has access")
        void adminAllowed() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);
            assertTrue(security.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }

        @Test
        @DisplayName("Clan Head has access to events in own clan")
        void clanHeadAllowedInOwnClan() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
            when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);

            assertTrue(security.hasContextualRole(EVENT_ID, "EventTreasurer"));
        }

        @Test
        @DisplayName("Clan Head denied for events not in own clan")
        void clanHeadDeniedForForeignEvent() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
            when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(false);

            assertFalse(security.hasContextualRole(EVENT_ID, "EventTreasurer"));
        }
    }

    @Nested
    @DisplayName("Contextual event personnel authorization")
    class ContextualPersonnel {

        @BeforeEach
        void initMember() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
        }

        @Test
        @DisplayName("Member without linked profile is denied")
        void noMemberIdDenied() {
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.empty());
            assertFalse(security.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }

        @Test
        @DisplayName("Event not found in clan is denied")
        void eventNotFoundDenied() {
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.empty());
            assertFalse(security.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }

        @Test
        @DisplayName("Closed event freezes all contextual write permissions")
        void closedEventDenied() {
            Event closedEvent = Event.builder()
                    .eventId(EVENT_ID)
                    .clanId(CLAN_ID)
                    .status(EventStatus.Closed)
                    .build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(closedEvent));

            assertFalse(security.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }

        @Test
        @DisplayName("Authorized when member has required contextual role")
        void matchingRoleAllowed() {
            Event event = Event.builder()
                    .eventId(EVENT_ID)
                    .clanId(CLAN_ID)
                    .status(EventStatus.Ongoing)
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

            assertTrue(security.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
            assertTrue(security.hasContextualRole(EVENT_ID, EventRole.HeadOfOrganizer));
        }

        @Test
        @DisplayName("Denied when member lacks required contextual role")
        void nonMatchingRoleDenied() {
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

            assertFalse(security.hasContextualRole(EVENT_ID, "EventTreasurer"));
            assertFalse(security.hasContextualRole(EVENT_ID, EventRole.EventTreasurer));
        }

        @Test
        @DisplayName("Denied when permission is marked as read-only")
        void readOnlyPermissionDenied() {
            Event event = Event.builder()
                    .eventId(EVENT_ID)
                    .clanId(CLAN_ID)
                    .status(EventStatus.Ongoing)
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

            assertFalse(security.hasContextualRole(EVENT_ID, "HeadOfOrganizer"));
        }
    }

    @Test
    @DisplayName("Invalid eventId returns false")
    void invalidEventId() {
        assertFalse(security.hasContextualRole(null, "HeadOfOrganizer"));
        assertFalse(security.hasContextualRole("", "HeadOfOrganizer"));
        assertFalse(security.hasContextualRole("   ", "HeadOfOrganizer"));
    }
}
