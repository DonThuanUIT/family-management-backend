package com.familyfund.event.internal.service;

import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.event.api.dto.RegistrationSummaryDto;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.mapper.RegistrationMapper;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.FamilyRegistrationRepository;
import com.familyfund.event.internal.repository.RegistrationAggregateProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventQueryServiceImpl Unit Tests")
class EventQueryServiceImplTest {

    private static final String EVENT_ID = "EVT_TEST_01";
    private static final String CLAN_ID = "CLAN_01";

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventPersonnelRepository eventPersonnelRepository;

    @Mock
    private FamilyRegistrationRepository familyRegistrationRepository;

    @Spy
    private RegistrationMapper registrationMapper = Mappers.getMapper(RegistrationMapper.class);

    @InjectMocks
    private EventQueryServiceImpl eventQueryService;

    @Test
    @DisplayName("Should find event summary when event exists")
    void findEventSummary_exists() {
        Event event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Lễ Chạp Họ")
                .status(EventStatus.RegistrationOpen)
                .eventDatetime(Instant.now())
                .build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        Optional<EventSummaryDto> result = eventQueryService.findEventSummary(EVENT_ID);

        assertTrue(result.isPresent());
        assertEquals("Lễ Chạp Họ", result.get().eventName());
        assertEquals(EventStatus.RegistrationOpen, result.get().status());
    }

    @Test
    @DisplayName("Should check if event is closed")
    void isEventClosed() {
        Event openEvent = Event.builder().eventId(EVENT_ID).status(EventStatus.RegistrationOpen).build();
        Event closedEvent = Event.builder().eventId("EVT_CLOSED").status(EventStatus.Closed).build();

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(openEvent));
        when(eventRepository.findById("EVT_CLOSED")).thenReturn(Optional.of(closedEvent));
        when(eventRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertFalse(eventQueryService.isEventClosed(EVENT_ID));
        assertTrue(eventQueryService.isEventClosed("EVT_CLOSED"));
        assertFalse(eventQueryService.isEventClosed("UNKNOWN"));
    }

    @Test
    @DisplayName("Should retrieve member roles in event")
    void getMemberRolesInEvent() {
        EventPersonnel p1 = EventPersonnel.builder().eventId(EVENT_ID).memberId("MEM_01").eventRole(EventRole.HeadOfOrganizer).build();
        EventPersonnel p2 = EventPersonnel.builder().eventId(EVENT_ID).memberId("MEM_01").eventRole(EventRole.OrganizerMember).build();

        when(eventPersonnelRepository.findAllByEventIdAndMemberId(EVENT_ID, "MEM_01")).thenReturn(List.of(p1, p2));

        Set<EventRole> roles = eventQueryService.getMemberRolesInEvent(EVENT_ID, "MEM_01");

        assertEquals(2, roles.size());
        assertTrue(roles.contains(EventRole.HeadOfOrganizer));
        assertTrue(roles.contains(EventRole.OrganizerMember));
    }

    @Test
    @DisplayName("Should return registration summary projection mapped to DTO")
    void getRegistrationSummary() {
        Event event = Event.builder().eventId(EVENT_ID).clanId(CLAN_ID).build();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        RegistrationAggregateProjection projection = new RegistrationAggregateProjection() {
            @Override
            public long getTotalRegistrations() {
                return 10;
            }

            @Override
            public long getTotalAttendees() {
                return 35;
            }

            @Override
            public long getTotalVegetarianMeals() {
                return 5;
            }

            @Override
            public long getTotalSavoryMeals() {
                return 30;
            }
        };

        when(familyRegistrationRepository.getAggregateSummary(EVENT_ID, CLAN_ID)).thenReturn(projection);

        Optional<RegistrationSummaryDto> summary = eventQueryService.getRegistrationSummary(EVENT_ID);

        assertTrue(summary.isPresent());
        assertEquals(EVENT_ID, summary.get().eventId());
        assertEquals(10, summary.get().totalRegistrations());
        assertEquals(35, summary.get().totalAttendees());
        assertEquals(5, summary.get().totalVegetarianMeals());
        assertEquals(30, summary.get().totalSavoryMeals());
    }
}
