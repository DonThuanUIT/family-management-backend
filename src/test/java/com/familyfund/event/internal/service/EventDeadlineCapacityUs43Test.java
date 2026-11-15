package com.familyfund.event.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.RegistrationStatus;
import com.familyfund.event.internal.dto.request.CreateEventRequest;
import com.familyfund.event.internal.dto.request.RegisterAttendeesRequest;
import com.familyfund.event.internal.dto.request.UpdateRegistrationRequest;
import com.familyfund.event.internal.dto.response.EventDetailResponse;
import com.familyfund.event.internal.dto.response.EventResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.FamilyRegistration;
import com.familyfund.event.internal.mapper.EventMapper;
import com.familyfund.event.internal.mapper.RegistrationMapper;
import com.familyfund.event.internal.repository.*;
import com.familyfund.event.internal.util.EventIdGenerator;
import com.familyfund.event.internal.util.RegistrationIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-43 Registration Deadline & Max Capacity Tests")
class EventDeadlineCapacityUs43Test {

    private static final String CLAN_ID = "CLAN_01";
    private static final String EVENT_ID = "EVT_43";
    private static final String ACCOUNT_ID = "ACC_01";
    private static final String MEMBER_ID = "MEM_01";

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventPersonnelRepository eventPersonnelRepository;
    @Mock
    private EventNotificationRepository eventNotificationRepository;
    @Mock
    private EventIdGenerator eventIdGenerator;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private GenealogyQueryService genealogyQueryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FamilyRegistrationRepository familyRegistrationRepository;
    @Mock
    private RegistrationMemberDetailRepository registrationMemberDetailRepository;
    @Mock
    private RegistrationIdGenerator registrationIdGenerator;

    private final EventMapper eventMapper = Mappers.getMapper(EventMapper.class);
    private final RegistrationMapper registrationMapper = Mappers.getMapper(RegistrationMapper.class);

    private EventServiceImpl eventService;
    private RegistrationServiceImpl registrationService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        eventService = new EventServiceImpl(
                eventRepository,
                eventPersonnelRepository,
                eventNotificationRepository,
                eventMapper,
                eventIdGenerator,
                currentUserProvider,
                genealogyQueryService,
                eventPublisher
        );
        registrationService = new RegistrationServiceImpl(
                eventRepository,
                familyRegistrationRepository,
                registrationMemberDetailRepository,
                eventPersonnelRepository,
                genealogyQueryService,
                currentUserProvider,
                registrationIdGenerator,
                registrationMapper,
                eventPublisher
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createEvent saves registrationDeadline and maxCapacity and getEventDetail computes remainingSlots")
    void createAndGetDetail_includesDeadlineAndRemainingSlots() {
        Instant deadline = Instant.now().plus(3, ChronoUnit.DAYS);
        CreateEventRequest req = new CreateEventRequest(
                "Giỗ Tổ 2026",
                Instant.now().plus(7, ChronoUnit.DAYS),
                "10/03",
                "Từ đường",
                BigDecimal.valueOf(5000000),
                "Mô tả",
                deadline,
                100
        );

        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACCOUNT_ID));
        when(eventIdGenerator.generate()).thenReturn(EVENT_ID);
        when(eventRepository.existsById(EVENT_ID)).thenReturn(false);
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.sumActiveAttendeesByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(35L);

        EventResponse created = eventService.createEvent(req);
        assertThat(created.registrationDeadline()).isEqualTo(deadline);
        assertThat(created.maxCapacity()).isEqualTo(100);
        assertThat(created.currentAttendees()).isEqualTo(35);
        assertThat(created.remainingSlots()).isEqualTo(65);

        Event stored = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Giỗ Tổ 2026")
                .eventDatetime(req.eventDatetime())
                .location("Từ đường")
                .status(EventStatus.RegistrationOpen)
                .createdBy(ACCOUNT_ID)
                .registrationDeadline(deadline)
                .maxCapacity(100)
                .build();
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(stored));
        when(eventPersonnelRepository.findAllByEventId(EVENT_ID)).thenReturn(List.of());

        EventDetailResponse detail = eventService.getEventDetail(EVENT_ID);
        assertThat(detail.registrationDeadline()).isEqualTo(deadline);
        assertThat(detail.maxCapacity()).isEqualTo(100);
        assertThat(detail.currentAttendees()).isEqualTo(35);
        assertThat(detail.remainingSlots()).isEqualTo(65);
    }

    @Test
    @DisplayName("registerAttendees throws BusinessRuleException when registrationDeadline has passed")
    void registerAttendees_afterDeadline_rejected() {
        Event expiredEvent = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Giỗ Tổ")
                .status(EventStatus.RegistrationOpen)
                .registrationDeadline(Instant.now().minus(1, ChronoUnit.HOURS))
                .maxCapacity(50)
                .build();
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(expiredEvent));

        RegisterAttendeesRequest request = new RegisterAttendeesRequest(
                MEMBER_ID, 2, 0, 2, null, null, List.of()
        );

        assertThatThrownBy(() -> registrationService.registerAttendees(EVENT_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("hết hạn đăng ký");
    }

    @Test
    @DisplayName("registerAttendees throws BusinessRuleException when maxCapacity is exceeded")
    void registerAttendees_exceedsMaxCapacity_rejected() {
        Event event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Giỗ Tổ")
                .status(EventStatus.RegistrationOpen)
                .registrationDeadline(Instant.now().plus(2, ChronoUnit.DAYS))
                .maxCapacity(10)
                .build();

        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
        when(genealogyQueryService.findMemberSummary(MEMBER_ID))
                .thenReturn(Optional.of(new MemberSummaryDto(MEMBER_ID, "Nguyễn Văn A", CLAN_ID, "BR_01")));
        when(eventRepository.sumActiveAttendeesByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(8L);

        RegisterAttendeesRequest request = new RegisterAttendeesRequest(
                MEMBER_ID, 3, 0, 3, null, null, List.of()
        );

        assertThatThrownBy(() -> registrationService.registerAttendees(EVENT_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sức chứa tối đa");
    }

    @Test
    @DisplayName("updateRegistration throws BusinessRuleException when increasing attendees beyond maxCapacity")
    void updateRegistration_exceedsMaxCapacity_rejected() {
        Event event = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Giỗ Tổ")
                .status(EventStatus.RegistrationOpen)
                .registrationDeadline(Instant.now().plus(2, ChronoUnit.DAYS))
                .maxCapacity(10)
                .build();
        FamilyRegistration reg = FamilyRegistration.builder()
                .registrationId("REG_01")
                .eventId(EVENT_ID)
                .representativeMemberId(MEMBER_ID)
                .totalAttendees(2)
                .status(RegistrationStatus.Confirmed)
                .build();

        when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId("REG_01", CLAN_ID))
                .thenReturn(Optional.of(reg));
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));
        when(eventRepository.sumActiveAttendeesByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(9L);

        UpdateRegistrationRequest req = new UpdateRegistrationRequest(4, 0, 4, null, null);

        assertThatThrownBy(() -> registrationService.updateRegistration("REG_01", req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sức chứa tối đa");
    }
}
