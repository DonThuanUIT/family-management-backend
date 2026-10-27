package com.familyfund.event.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.event.EventOpenedDomainEvent;
import com.familyfund.event.api.event.EventScheduleChangedDomainEvent;
import com.familyfund.event.internal.dto.request.AssignPersonnelRequest;
import com.familyfund.event.internal.dto.request.CreateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventStatusRequest;
import com.familyfund.event.internal.dto.response.EventDetailResponse;
import com.familyfund.event.internal.dto.response.EventNotificationResponse;
import com.familyfund.event.internal.dto.response.EventPersonnelResponse;
import com.familyfund.event.internal.dto.response.EventResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventNotification;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.mapper.EventMapper;
import com.familyfund.event.internal.repository.EventNotificationRepository;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.util.EventIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceImplTest {

    private static final String CLAN_ID = "CLAN_NGUYEN";
    private static final String ACCOUNT_ID = "ACC_CREATOR";
    private static final String EVENT_ID = "EVT_2026_TEST";

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventPersonnelRepository eventPersonnelRepository;

    @Mock
    private EventNotificationRepository eventNotificationRepository;

    @Spy
    private EventMapper eventMapper = Mappers.getMapper(EventMapper.class);

    @Mock
    private EventIdGenerator eventIdGenerator;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EventServiceImpl eventService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACCOUNT_ID));
        when(eventIdGenerator.generate()).thenReturn(EVENT_ID);
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Create event")
    class CreateEvent {

        @Test
        @DisplayName("Successfully creates event with status Draft and generated ID")
        void success() {
            CreateEventRequest request = new CreateEventRequest(
                    "Giỗ Tổ 2026",
                    Instant.parse("2026-04-26T08:00:00Z"),
                    "10/03 Bính Ngọ",
                    "Từ đường Họ Nguyễn",
                    BigDecimal.valueOf(50_000_000),
                    "Lễ giỗ tổ truyền thống"
            );

            EventResponse response = eventService.createEvent(request);

            assertNotNull(response);
            assertEquals(EVENT_ID, response.eventId());
            assertEquals(CLAN_ID, response.clanId());
            assertEquals("Giỗ Tổ 2026", response.eventName());
            assertEquals(EventStatus.Draft, response.status());
            assertEquals(ACCOUNT_ID, response.createdBy());
            assertEquals(BigDecimal.valueOf(50_000_000), response.estimatedBudget());

            ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
            verify(eventRepository).saveAndFlush(captor.capture());
            Event saved = captor.getValue();
            assertEquals(EVENT_ID, saved.getEventId());
            assertEquals(CLAN_ID, saved.getClanId());
            assertEquals(EventStatus.Draft, saved.getStatus());
        }

        @Test
        @DisplayName("Fails when estimated budget is negative")
        void negativeBudget() {
            CreateEventRequest request = new CreateEventRequest(
                    "Giỗ Tổ 2026",
                    Instant.parse("2026-04-26T08:00:00Z"),
                    null,
                    "Từ đường Họ Nguyễn",
                    BigDecimal.valueOf(-100),
                    null
            );

            assertThrows(BadRequestException.class, () -> eventService.createEvent(request));
        }

        @Test
        @DisplayName("Fails when caller account is missing")
        void unauthenticatedCaller() {
            when(currentUserProvider.currentAccountId()).thenReturn(Optional.empty());

            CreateEventRequest request = new CreateEventRequest(
                    "Giỗ Tổ 2026",
                    Instant.parse("2026-04-26T08:00:00Z"),
                    null,
                    "Từ đường Họ Nguyễn",
                    BigDecimal.ZERO,
                    null
            );

            assertThrows(BadRequestException.class, () -> eventService.createEvent(request));
        }
    }

    @Nested
    @DisplayName("Update event details & schedule changes")
    class UpdateEvent {

        @Test
        @DisplayName("Successfully updates event and publishes EventScheduleChangedDomainEvent when schedule changes")
        void updateWithScheduleChanges() {
            Event event = Event.builder()
                    .eventId(EVENT_ID)
                    .clanId(CLAN_ID)
                    .eventName("Giỗ Tổ 2026")
                    .eventDatetime(Instant.parse("2026-04-26T08:00:00Z"))
                    .eventDateLunar("10/03 Bính Ngọ")
                    .location("Nhà thờ tổ Chi 1")
                    .estimatedBudget(BigDecimal.valueOf(50_000_000))
                    .status(EventStatus.Draft)
                    .build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            Instant newTime = Instant.parse("2026-04-27T09:00:00Z");
            UpdateEventRequest request = new UpdateEventRequest(
                    "Giỗ Tổ 2026 (Điều chỉnh)",
                    newTime,
                    "11/03 Bính Ngọ",
                    "Từ đường Đại Tộc",
                    BigDecimal.valueOf(55_000_000),
                    "Đổi sang từ đường lớn"
            );

            EventResponse response = eventService.updateEvent(EVENT_ID, request);

            assertNotNull(response);
            assertEquals("Giỗ Tổ 2026 (Điều chỉnh)", event.getEventName());
            assertEquals(newTime, event.getEventDatetime());
            assertEquals("11/03 Bính Ngọ", event.getEventDateLunar());
            assertEquals("Từ đường Đại Tộc", event.getLocation());

            ArgumentCaptor<EventScheduleChangedDomainEvent> eventCaptor =
                    ArgumentCaptor.forClass(EventScheduleChangedDomainEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            EventScheduleChangedDomainEvent published = eventCaptor.getValue();

            assertEquals(EVENT_ID, published.eventId());
            assertEquals(CLAN_ID, published.clanId());
            assertTrue(published.contentChanges().contains("Thời gian dương lịch"));
            assertTrue(published.contentChanges().contains("Ngày âm lịch"));
            assertTrue(published.contentChanges().contains("Địa điểm"));
        }

        @Test
        @DisplayName("Does not publish schedule change event when only description or name changes")
        void updateWithoutScheduleChanges() {
            Instant time = Instant.parse("2026-04-26T08:00:00Z");
            Event event = Event.builder()
                    .eventId(EVENT_ID)
                    .clanId(CLAN_ID)
                    .eventName("Giỗ Tổ 2026")
                    .eventDatetime(time)
                    .eventDateLunar("10/03 Bính Ngọ")
                    .location("Nhà thờ tổ Chi 1")
                    .estimatedBudget(BigDecimal.valueOf(50_000_000))
                    .status(EventStatus.Draft)
                    .build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            UpdateEventRequest request = new UpdateEventRequest(
                    "Giỗ Tổ 2026",
                    time,
                    "10/03 Bính Ngọ",
                    "Nhà thờ tổ Chi 1",
                    BigDecimal.valueOf(50_000_000),
                    "Cập nhật thêm lưu ý trang phục"
            );

            eventService.updateEvent(EVENT_ID, request);

            verify(eventPublisher, never()).publishEvent(any(EventScheduleChangedDomainEvent.class));
        }

        @Test
        @DisplayName("Fails to update when event is Closed")
        void updateClosedEventFails() {
            Event event = Event.builder()
                    .eventId(EVENT_ID)
                    .clanId(CLAN_ID)
                    .status(EventStatus.Closed)
                    .build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            UpdateEventRequest request = new UpdateEventRequest(
                    "Tên mới", Instant.now(), null, "Địa điểm", BigDecimal.ZERO, null
            );

            assertThrows(BusinessRuleException.class, () -> eventService.updateEvent(EVENT_ID, request));
        }
    }

    @Nested
    @DisplayName("Lifecycle status transitions")
    class LifecycleTransitions {

        @Test
        @DisplayName("Valid step-by-step transition from Draft to Closed, publishes EventOpenedDomainEvent on RegistrationOpen")
        void validTransitionChain() {
            Event event = Event.builder()
                    .eventId(EVENT_ID)
                    .clanId(CLAN_ID)
                    .eventName("Giỗ Tổ")
                    .eventDatetime(Instant.parse("2026-04-26T08:00:00Z"))
                    .location("Từ đường")
                    .status(EventStatus.Draft)
                    .build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            // Draft -> RegistrationOpen (should publish EventOpenedDomainEvent)
            eventService.updateEventStatus(EVENT_ID, new UpdateEventStatusRequest(EventStatus.RegistrationOpen));
            assertEquals(EventStatus.RegistrationOpen, event.getStatus());
            verify(eventPublisher).publishEvent(any(EventOpenedDomainEvent.class));

            // RegistrationOpen -> RegistrationClosed
            eventService.updateEventStatus(EVENT_ID, new UpdateEventStatusRequest(EventStatus.RegistrationClosed));
            assertEquals(EventStatus.RegistrationClosed, event.getStatus());

            // RegistrationClosed -> Ongoing
            eventService.updateEventStatus(EVENT_ID, new UpdateEventStatusRequest(EventStatus.Ongoing));
            assertEquals(EventStatus.Ongoing, event.getStatus());

            // Ongoing -> Ended
            eventService.updateEventStatus(EVENT_ID, new UpdateEventStatusRequest(EventStatus.Ended));
            assertEquals(EventStatus.Ended, event.getStatus());

            // Ended -> Closed
            EventPersonnel personnel = EventPersonnel.builder()
                    .eventId(EVENT_ID)
                    .memberId("MEM_01")
                    .eventRole(EventRole.HeadOfOrganizer)
                    .isReadOnly(false)
                    .build();
            when(eventPersonnelRepository.findAllByEventId(EVENT_ID)).thenReturn(List.of(personnel));

            eventService.updateEventStatus(EVENT_ID, new UpdateEventStatusRequest(EventStatus.Closed));
            assertEquals(EventStatus.Closed, event.getStatus());
            assertNotNull(event.getClosedAt());
            assertTrue(personnel.getIsReadOnly());
            verify(eventPersonnelRepository).saveAll(any());
        }

        @Test
        @DisplayName("Illegal transition throws BusinessRuleException")
        void invalidTransition() {
            Event event = Event.builder()
                    .eventId(EVENT_ID)
                    .clanId(CLAN_ID)
                    .status(EventStatus.Draft)
                    .build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            assertThrows(BusinessRuleException.class, () ->
                    eventService.updateEventStatus(EVENT_ID, new UpdateEventStatusRequest(EventStatus.Ongoing)));
        }

        @Test
        @DisplayName("Updating non-existent event throws ResourceNotFoundException")
        void eventNotFound() {
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    eventService.updateEventStatus(EVENT_ID, new UpdateEventStatusRequest(EventStatus.RegistrationOpen)));
        }
    }

    @Nested
    @DisplayName("Assign event personnel")
    class AssignPersonnel {

        @Test
        @DisplayName("Successfully assigns HeadOfOrganizer, EventTreasurer, and OrganizerMember")
        void success() {
            Event event = Event.builder()
                    .eventId(EVENT_ID)
                    .clanId(CLAN_ID)
                    .status(EventStatus.Draft)
                    .build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            when(genealogyQueryService.findMemberSummary("MEM_HEAD"))
                    .thenReturn(Optional.of(new MemberSummaryDto("MEM_HEAD", "Nguyễn Văn Trưởng", CLAN_ID, "BR_1")));
            when(genealogyQueryService.findMemberSummary("MEM_TREASURER"))
                    .thenReturn(Optional.of(new MemberSummaryDto("MEM_TREASURER", "Nguyễn Thị Quỹ", CLAN_ID, "BR_1")));
            when(genealogyQueryService.findMemberSummary("MEM_MEMBER"))
                    .thenReturn(Optional.of(new MemberSummaryDto("MEM_MEMBER", "Nguyễn Văn Viên", CLAN_ID, "BR_1")));

            when(eventPersonnelRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

            AssignPersonnelRequest request = new AssignPersonnelRequest(List.of(
                    new AssignPersonnelRequest.PersonnelItem("MEM_HEAD", EventRole.HeadOfOrganizer),
                    new AssignPersonnelRequest.PersonnelItem("MEM_TREASURER", EventRole.EventTreasurer),
                    new AssignPersonnelRequest.PersonnelItem("MEM_MEMBER", EventRole.OrganizerMember)
            ));

            List<EventPersonnelResponse> result = eventService.assignPersonnel(EVENT_ID, request);

            assertEquals(3, result.size());
            verify(eventPersonnelRepository).deleteAllByEventId(EVENT_ID);
            verify(eventPersonnelRepository).saveAllAndFlush(any());
        }

        @Test
        @DisplayName("Rejects assigning multiple HeadOfOrganizer")
        void multipleHeadsRejected() {
            Event event = Event.builder().eventId(EVENT_ID).clanId(CLAN_ID).status(EventStatus.Draft).build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            AssignPersonnelRequest request = new AssignPersonnelRequest(List.of(
                    new AssignPersonnelRequest.PersonnelItem("MEM_1", EventRole.HeadOfOrganizer),
                    new AssignPersonnelRequest.PersonnelItem("MEM_2", EventRole.HeadOfOrganizer)
            ));

            assertThrows(BusinessRuleException.class, () -> eventService.assignPersonnel(EVENT_ID, request));
        }

        @Test
        @DisplayName("Rejects assigning multiple EventTreasurer")
        void multipleTreasurersRejected() {
            Event event = Event.builder().eventId(EVENT_ID).clanId(CLAN_ID).status(EventStatus.Draft).build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            AssignPersonnelRequest request = new AssignPersonnelRequest(List.of(
                    new AssignPersonnelRequest.PersonnelItem("MEM_1", EventRole.EventTreasurer),
                    new AssignPersonnelRequest.PersonnelItem("MEM_2", EventRole.EventTreasurer)
            ));

            assertThrows(BusinessRuleException.class, () -> eventService.assignPersonnel(EVENT_ID, request));
        }

        @Test
        @DisplayName("Rejects member from another clan")
        void foreignClanMemberRejected() {
            Event event = Event.builder().eventId(EVENT_ID).clanId(CLAN_ID).status(EventStatus.Draft).build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            when(genealogyQueryService.findMemberSummary("MEM_OTHER"))
                    .thenReturn(Optional.of(new MemberSummaryDto("MEM_OTHER", "Trần Khác Tộc", "CLAN_TRAN", "BR_2")));

            AssignPersonnelRequest request = new AssignPersonnelRequest(List.of(
                    new AssignPersonnelRequest.PersonnelItem("MEM_OTHER", EventRole.HeadOfOrganizer)
            ));

            assertThrows(BusinessRuleException.class, () -> eventService.assignPersonnel(EVENT_ID, request));
        }

        @Test
        @DisplayName("Rejects non-existent member")
        void memberNotFoundRejected() {
            Event event = Event.builder().eventId(EVENT_ID).clanId(CLAN_ID).status(EventStatus.Draft).build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            when(genealogyQueryService.findMemberSummary("MEM_404")).thenReturn(Optional.empty());

            AssignPersonnelRequest request = new AssignPersonnelRequest(List.of(
                    new AssignPersonnelRequest.PersonnelItem("MEM_404", EventRole.HeadOfOrganizer)
            ));

            assertThrows(ResourceNotFoundException.class, () -> eventService.assignPersonnel(EVENT_ID, request));
        }

        @Test
        @DisplayName("Rejects personnel changes when event is already closed")
        void closedEventRejected() {
            Event event = Event.builder().eventId(EVENT_ID).clanId(CLAN_ID).status(EventStatus.Closed).build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            AssignPersonnelRequest request = new AssignPersonnelRequest(List.of(
                    new AssignPersonnelRequest.PersonnelItem("MEM_1", EventRole.HeadOfOrganizer)
            ));

            assertThrows(BusinessRuleException.class, () -> eventService.assignPersonnel(EVENT_ID, request));
        }
    }

    @Nested
    @DisplayName("Query detail, list, and notifications")
    class Queries {

        @Test
        @DisplayName("Returns event detail with mapped personnel names")
        void eventDetail() {
            Event event = Event.builder()
                    .eventId(EVENT_ID)
                    .clanId(CLAN_ID)
                    .eventName("Lễ Chạp")
                    .status(EventStatus.Draft)
                    .build();
            when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

            EventPersonnel p = EventPersonnel.builder()
                    .eventId(EVENT_ID)
                    .memberId("MEM_1")
                    .eventRole(EventRole.HeadOfOrganizer)
                    .build();
            when(eventPersonnelRepository.findAllByEventId(EVENT_ID)).thenReturn(List.of(p));
            when(genealogyQueryService.findMemberSummary("MEM_1"))
                    .thenReturn(Optional.of(new MemberSummaryDto("MEM_1", "Nguyễn Văn A", CLAN_ID, "BR_1")));

            EventDetailResponse detail = eventService.getEventDetail(EVENT_ID);
            assertNotNull(detail);
            assertEquals(EVENT_ID, detail.eventId());
            assertEquals(1, detail.personnel().size());
            assertEquals("Nguyễn Văn A", detail.personnel().get(0).memberName());
        }

        @Test
        @DisplayName("Returns event list for current clan")
        void listEvents() {
            Event event = Event.builder().eventId(EVENT_ID).clanId(CLAN_ID).eventName("Lễ").status(EventStatus.Draft).build();
            when(eventRepository.findAllByClanIdOrderByEventDatetimeDesc(CLAN_ID)).thenReturn(List.of(event));

            List<EventResponse> list = eventService.listEvents();
            assertEquals(1, list.size());
            assertEquals(EVENT_ID, list.get(0).eventId());
        }

        @Test
        @DisplayName("Returns event notifications ordered by sentTime")
        void getEventNotifications() {
            when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);

            EventNotification notif = EventNotification.builder()
                    .notificationId("NTF_1")
                    .eventId(EVENT_ID)
                    .title("Thông báo mở đăng ký")
                    .contentChanges("Chi tiết mở đăng ký")
                    .sentTime(Instant.now())
                    .triggeredBy(ACCOUNT_ID)
                    .build();
            when(eventNotificationRepository.findAllByEventIdOrderBySentTimeDesc(EVENT_ID))
                    .thenReturn(List.of(notif));

            List<EventNotificationResponse> notifications = eventService.getEventNotifications(EVENT_ID);
            assertEquals(1, notifications.size());
            assertEquals("NTF_1", notifications.get(0).notificationId());
            assertEquals("Thông báo mở đăng ký", notifications.get(0).title());
        }

        @Test
        @DisplayName("getEventNotifications throws ResourceNotFoundException for non-existent event")
        void getEventNotificationsNotFound() {
            when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () -> eventService.getEventNotifications(EVENT_ID));
        }
    }
}
