package com.familyfund.event.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.DuplicateResourceException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.RegistrationStatus;
import com.familyfund.event.api.event.FamilyRegistrationSubmittedDomainEvent;
import com.familyfund.event.api.event.RegistrationModificationRequestedDomainEvent;
import com.familyfund.event.api.event.RegistrationReviewedDomainEvent;
import com.familyfund.event.internal.dto.request.RegisterAttendeesRequest;
import com.familyfund.event.internal.dto.request.RequestCancelRegistrationRequest;
import com.familyfund.event.internal.dto.request.RequestModifyRegistrationRequest;
import com.familyfund.event.internal.dto.request.ReviewRegistrationRequest;
import com.familyfund.event.internal.dto.response.RegistrationResponse;
import com.familyfund.event.internal.dto.response.RegistrationSummaryResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.FamilyRegistration;
import com.familyfund.event.internal.entity.RegistrationMemberDetail;
import com.familyfund.event.internal.mapper.RegistrationMapper;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.FamilyRegistrationRepository;
import com.familyfund.event.internal.repository.RegistrationAggregateProjection;
import com.familyfund.event.internal.repository.RegistrationMemberDetailRepository;
import com.familyfund.event.internal.util.RegistrationIdGenerator;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RegistrationServiceImpl Unit Tests")
class RegistrationServiceImplTest {

    private static final String CLAN_ID = "CLAN_TEST";
    private static final String ACCOUNT_ID = "ACC_001";
    private static final String EVENT_ID = "EVT_TEST_01";
    private static final String REG_ID = "REG_20261101_ABC123";
    private static final String REP_MEMBER_ID = "MEM_REP_01";
    private static final String ATTENDEE_1_ID = "MEM_ATT_01";
    private static final String ATTENDEE_2_ID = "MEM_ATT_02";

    @Mock
    private EventRepository eventRepository;

    @Mock
    private FamilyRegistrationRepository familyRegistrationRepository;

    @Mock
    private RegistrationMemberDetailRepository registrationMemberDetailRepository;

    @Mock
    private EventPersonnelRepository eventPersonnelRepository;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private RegistrationIdGenerator registrationIdGenerator;

    @Spy
    private RegistrationMapper registrationMapper = Mappers.getMapper(RegistrationMapper.class);

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    private Event activeEvent;
    private MemberSummaryDto repSummary;
    private MemberSummaryDto attendee1Summary;
    private MemberSummaryDto attendee2Summary;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACCOUNT_ID));
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(REP_MEMBER_ID));
        when(registrationIdGenerator.generate()).thenReturn(REG_ID);

        activeEvent = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Đại lễ Cúng Tổ Mùa Xuân")
                .eventDatetime(Instant.now().plusSeconds(86400 * 10))
                .status(EventStatus.RegistrationOpen)
                .build();

        repSummary = new MemberSummaryDto(REP_MEMBER_ID, "Nguyễn Văn Đại Diện", CLAN_ID, null);
        attendee1Summary = new MemberSummaryDto(ATTENDEE_1_ID, "Nguyễn Văn Con Cả", CLAN_ID, null);
        attendee2Summary = new MemberSummaryDto(ATTENDEE_2_ID, "Nguyễn Thị Con Gái", CLAN_ID, null);

        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
        when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);
        when(genealogyQueryService.findMemberSummary(REP_MEMBER_ID)).thenReturn(Optional.of(repSummary));
        when(genealogyQueryService.findMemberSummary(ATTENDEE_1_ID)).thenReturn(Optional.of(attendee1Summary));
        when(genealogyQueryService.findMemberSummary(ATTENDEE_2_ID)).thenReturn(Optional.of(attendee2Summary));
        when(familyRegistrationRepository.save(any(FamilyRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("registerAttendees Tests")
    class RegisterAttendeesTests {

        @Test
        @DisplayName("Should successfully register attendance with valid inputs and details")
        void registerAttendees_successful() {
            RegisterAttendeesRequest request = new RegisterAttendeesRequest(
                    null,
                    4,
                    1,
                    3,
                    "1 người ăn chay thanh tịnh",
                    "Gia đình tự túc xe ô tô 4 chỗ",
                    List.of(ATTENDEE_1_ID, ATTENDEE_2_ID)
            );

            when(familyRegistrationRepository.existsByEventIdAndRepresentativeMemberIdAndStatusNot(
                    EVENT_ID, REP_MEMBER_ID, RegistrationStatus.Cancelled)).thenReturn(false);

            RegistrationResponse response = registrationService.registerAttendees(EVENT_ID, request);

            assertNotNull(response);
            assertEquals(REG_ID, response.registrationId());
            assertEquals(EVENT_ID, response.eventId());
            assertEquals(REP_MEMBER_ID, response.representativeMemberId());
            assertEquals("Nguyễn Văn Đại Diện", response.representativeMemberName());
            assertEquals(4, response.totalAttendees());
            assertEquals(1, response.vegetarianMeals());
            assertEquals(3, response.savoryMeals());
            assertEquals("1 người ăn chay thanh tịnh", response.dietaryRequirements());
            assertEquals("Gia đình tự túc xe ô tô 4 chỗ", response.transportationNotes());
            assertEquals(RegistrationStatus.Confirmed, response.status());
            assertEquals(2, response.attendingMembers().size());

            verify(familyRegistrationRepository).save(any(FamilyRegistration.class));
            verify(registrationMemberDetailRepository).saveAll(anyList());

            ArgumentCaptor<FamilyRegistrationSubmittedDomainEvent> eventCaptor =
                    ArgumentCaptor.forClass(FamilyRegistrationSubmittedDomainEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            FamilyRegistrationSubmittedDomainEvent publishedEvent = eventCaptor.getValue();
            assertEquals(REG_ID, publishedEvent.registrationId());
            assertEquals(EVENT_ID, publishedEvent.eventId());
            assertEquals(CLAN_ID, publishedEvent.clanId());
            assertEquals(REP_MEMBER_ID, publishedEvent.representativeMemberId());
            assertEquals(4, publishedEvent.totalAttendees());
            assertEquals(ACCOUNT_ID, publishedEvent.triggeredBy());
        }

        @Test
        @DisplayName("Should register on behalf of another member if caller is CLAN_HEAD or ADMIN")
        void registerAttendees_privilegedUserOnBehalfOfOther() {
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_ORGANIZER"));

            String otherMemberId = "MEM_OTHER_01";
            MemberSummaryDto otherSummary = new MemberSummaryDto(otherMemberId, "Trần Văn Khác", CLAN_ID, null);
            when(genealogyQueryService.findMemberSummary(otherMemberId)).thenReturn(Optional.of(otherSummary));

            RegisterAttendeesRequest request = new RegisterAttendeesRequest(
                    otherMemberId,
                    2,
                    0,
                    2,
                    null,
                    null,
                    null
            );

            when(familyRegistrationRepository.existsByEventIdAndRepresentativeMemberIdAndStatusNot(
                    EVENT_ID, otherMemberId, RegistrationStatus.Cancelled)).thenReturn(false);

            RegistrationResponse response = registrationService.registerAttendees(EVENT_ID, request);

            assertNotNull(response);
            assertEquals(otherMemberId, response.representativeMemberId());
            assertEquals("Trần Văn Khác", response.representativeMemberName());
            assertEquals(2, response.totalAttendees());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when event does not exist")
        void registerAttendees_eventNotFound() {
            when(eventRepository.findByEventIdAndClanId("UNKNOWN_EVT", CLAN_ID)).thenReturn(Optional.empty());

            RegisterAttendeesRequest request = new RegisterAttendeesRequest(null, 2, 0, 2, null, null, null);

            assertThrows(ResourceNotFoundException.class, () ->
                    registrationService.registerAttendees("UNKNOWN_EVT", request));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when event is not in RegistrationOpen status")
        void registerAttendees_eventNotRegistrationOpen() {
            activeEvent.setStatus(EventStatus.Draft);

            RegisterAttendeesRequest request = new RegisterAttendeesRequest(null, 2, 0, 2, null, null, null);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                    registrationService.registerAttendees(EVENT_ID, request));
            assertTrue(ex.getMessage().contains("không ở trạng thái mở tiếp nhận đăng ký"));
        }

        @Test
        @DisplayName("Should throw BadRequestException when caller is not linked to member profile")
        void registerAttendees_callerNotLinkedToMember() {
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.empty());
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);

            RegisterAttendeesRequest request = new RegisterAttendeesRequest(null, 2, 0, 2, null, null, null);

            assertThrows(BadRequestException.class, () ->
                    registrationService.registerAttendees(EVENT_ID, request));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when representative not in genealogy")
        void registerAttendees_representativeNotInGenealogy() {
            when(genealogyQueryService.findMemberSummary(REP_MEMBER_ID)).thenReturn(Optional.empty());

            RegisterAttendeesRequest request = new RegisterAttendeesRequest(null, 2, 0, 2, null, null, null);

            assertThrows(ResourceNotFoundException.class, () ->
                    registrationService.registerAttendees(EVENT_ID, request));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when representative belongs to another clan")
        void registerAttendees_representativeDifferentClan() {
            MemberSummaryDto foreignMember = new MemberSummaryDto(REP_MEMBER_ID, "Người Họ Khác", "OTHER_CLAN", null);
            when(genealogyQueryService.findMemberSummary(REP_MEMBER_ID)).thenReturn(Optional.of(foreignMember));

            RegisterAttendeesRequest request = new RegisterAttendeesRequest(null, 2, 0, 2, null, null, null);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                    registrationService.registerAttendees(EVENT_ID, request));
            assertTrue(ex.getMessage().contains("không thuộc dòng họ hiện tại"));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when meals exceed total attendees")
        void registerAttendees_mealsExceedAttendees() {
            RegisterAttendeesRequest request = new RegisterAttendeesRequest(
                    null,
                    3,
                    2,
                    2, // total meals = 4 > 3
                    null,
                    null,
                    null
            );

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                    registrationService.registerAttendees(EVENT_ID, request));
            assertTrue(ex.getMessage().contains("không được vượt quá"));
        }

        @Test
        @DisplayName("Should throw DuplicateResourceException when household already has active registration")
        void registerAttendees_duplicateActiveRegistration() {
            RegisterAttendeesRequest request = new RegisterAttendeesRequest(null, 3, 1, 2, null, null, null);

            when(familyRegistrationRepository.existsByEventIdAndRepresentativeMemberIdAndStatusNot(
                    EVENT_ID, REP_MEMBER_ID, RegistrationStatus.Cancelled)).thenReturn(true);

            assertThrows(DuplicateResourceException.class, () ->
                    registrationService.registerAttendees(EVENT_ID, request));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when attending member list exceeds total attendees")
        void registerAttendees_attendingMembersExceedTotalAttendees() {
            RegisterAttendeesRequest request = new RegisterAttendeesRequest(
                    null,
                    1, // total attendees = 1
                    0,
                    1,
                    null,
                    null,
                    List.of(ATTENDEE_1_ID, ATTENDEE_2_ID) // 2 members > 1
            );

            when(familyRegistrationRepository.existsByEventIdAndRepresentativeMemberIdAndStatusNot(
                    EVENT_ID, REP_MEMBER_ID, RegistrationStatus.Cancelled)).thenReturn(false);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                    registrationService.registerAttendees(EVENT_ID, request));
            assertTrue(ex.getMessage().contains("vượt quá tổng số người tham dự"));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when attending member not found in genealogy")
        void registerAttendees_attendingMemberNotFound() {
            RegisterAttendeesRequest request = new RegisterAttendeesRequest(
                    null,
                    2,
                    0,
                    2,
                    null,
                    null,
                    List.of("UNKNOWN_MEMBER")
            );

            when(familyRegistrationRepository.existsByEventIdAndRepresentativeMemberIdAndStatusNot(
                    EVENT_ID, REP_MEMBER_ID, RegistrationStatus.Cancelled)).thenReturn(false);
            when(genealogyQueryService.findMemberSummary("UNKNOWN_MEMBER")).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    registrationService.registerAttendees(EVENT_ID, request));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when attending member belongs to another clan")
        void registerAttendees_attendingMemberDifferentClan() {
            RegisterAttendeesRequest request = new RegisterAttendeesRequest(
                    null,
                    2,
                    0,
                    2,
                    null,
                    null,
                    List.of(ATTENDEE_1_ID)
            );

            when(familyRegistrationRepository.existsByEventIdAndRepresentativeMemberIdAndStatusNot(
                    EVENT_ID, REP_MEMBER_ID, RegistrationStatus.Cancelled)).thenReturn(false);
            MemberSummaryDto foreignAttendee = new MemberSummaryDto(ATTENDEE_1_ID, "Họ Khác", "OTHER_CLAN", null);
            when(genealogyQueryService.findMemberSummary(ATTENDEE_1_ID)).thenReturn(Optional.of(foreignAttendee));

            assertThrows(BusinessRuleException.class, () ->
                    registrationService.registerAttendees(EVENT_ID, request));
        }
    }

    @Nested
    @DisplayName("getRegistrationSummary Tests")
    class GetRegistrationSummaryTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException when event does not exist")
        void getRegistrationSummary_eventNotFound() {
            when(eventRepository.existsByEventIdAndClanId("UNKNOWN", CLAN_ID)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () ->
                    registrationService.getRegistrationSummary("UNKNOWN"));
        }

        @Test
        @DisplayName("Should return aggregate metrics when event exists")
        void getRegistrationSummary_success() {
            RegistrationAggregateProjection projection = new RegistrationAggregateProjection() {
                @Override
                public long getTotalRegistrations() {
                    return 20;
                }

                @Override
                public long getTotalAttendees() {
                    return 68;
                }

                @Override
                public long getTotalVegetarianMeals() {
                    return 8;
                }

                @Override
                public long getTotalSavoryMeals() {
                    return 60;
                }
            };

            when(familyRegistrationRepository.getAggregateSummary(EVENT_ID, CLAN_ID)).thenReturn(projection);

            RegistrationSummaryResponse summary = registrationService.getRegistrationSummary(EVENT_ID);

            assertNotNull(summary);
            assertEquals(EVENT_ID, summary.eventId());
            assertEquals(20, summary.totalRegistrations());
            assertEquals(68, summary.totalAttendees());
            assertEquals(8, summary.totalVegetarianMeals());
            assertEquals(60, summary.totalSavoryMeals());
        }
    }

    @Nested
    @DisplayName("listRegistrations Tests")
    class ListRegistrationsTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException when event does not exist")
        void listRegistrations_eventNotFound() {
            when(eventRepository.existsByEventIdAndClanId("UNKNOWN", CLAN_ID)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () ->
                    registrationService.listRegistrations("UNKNOWN"));
        }

        @Test
        @DisplayName("Should return enriched list of registrations")
        void listRegistrations_success() {
            FamilyRegistration reg = FamilyRegistration.builder()
                    .registrationId(REG_ID)
                    .eventId(EVENT_ID)
                    .representativeMemberId(REP_MEMBER_ID)
                    .totalAttendees(3)
                    .vegetarianMeals(1)
                    .savoryMeals(2)
                    .registrationDate(Instant.now())
                    .status(RegistrationStatus.Confirmed)
                    .build();

            when(familyRegistrationRepository.findAllByEvent_EventIdAndEvent_ClanIdOrderByRegistrationDateDesc(EVENT_ID, CLAN_ID))
                    .thenReturn(List.of(reg));
            when(registrationMemberDetailRepository.findAllByRegistrationId(REG_ID)).thenReturn(List.of());

            List<RegistrationResponse> result = registrationService.listRegistrations(EVENT_ID);

            assertEquals(1, result.size());
            assertEquals(REG_ID, result.get(0).registrationId());
            assertEquals("Nguyễn Văn Đại Diện", result.get(0).representativeMemberName());
        }

        @Test
        @DisplayName("Should return paginated list of registrations")
        void listRegistrations_pageable_success() {
            FamilyRegistration reg = FamilyRegistration.builder()
                    .registrationId(REG_ID)
                    .eventId(EVENT_ID)
                    .representativeMemberId(REP_MEMBER_ID)
                    .totalAttendees(3)
                    .vegetarianMeals(1)
                    .savoryMeals(2)
                    .registrationDate(Instant.now())
                    .status(RegistrationStatus.Confirmed)
                    .build();

            Pageable pageable = PageRequest.of(0, 10);
            when(familyRegistrationRepository.findAllByEvent_EventIdAndEvent_ClanIdOrderByRegistrationDateDesc(EVENT_ID, CLAN_ID, pageable))
                    .thenReturn(new PageImpl<>(List.of(reg), pageable, 1));
            when(registrationMemberDetailRepository.findAllByRegistrationId(REG_ID)).thenReturn(List.of());

            Page<RegistrationResponse> pageResult = registrationService.listRegistrations(EVENT_ID, pageable);

            assertEquals(1, pageResult.getTotalElements());
            assertEquals(REG_ID, pageResult.getContent().get(0).registrationId());
        }
    }

    @Nested
    @DisplayName("getRegistrationById Tests")
    class GetRegistrationByIdTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException when registration not found")
        void getRegistrationById_notFound() {
            when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId("UNKNOWN", CLAN_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    registrationService.getRegistrationById(EVENT_ID, "UNKNOWN"));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when registration belongs to different event")
        void getRegistrationById_differentEvent() {
            FamilyRegistration reg = FamilyRegistration.builder()
                    .registrationId(REG_ID)
                    .eventId("OTHER_EVENT")
                    .representativeMemberId(REP_MEMBER_ID)
                    .build();

            when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(REG_ID, CLAN_ID))
                    .thenReturn(Optional.of(reg));

            assertThrows(ResourceNotFoundException.class, () ->
                    registrationService.getRegistrationById(EVENT_ID, REG_ID));
        }

        @Test
        @DisplayName("Should return registration with details when found")
        void getRegistrationById_success() {
            FamilyRegistration reg = FamilyRegistration.builder()
                    .registrationId(REG_ID)
                    .eventId(EVENT_ID)
                    .representativeMemberId(REP_MEMBER_ID)
                    .totalAttendees(2)
                    .vegetarianMeals(0)
                    .savoryMeals(2)
                    .registrationDate(Instant.now())
                    .status(RegistrationStatus.Confirmed)
                    .build();

            RegistrationMemberDetail detail = RegistrationMemberDetail.builder()
                    .registrationId(REG_ID)
                    .memberId(ATTENDEE_1_ID)
                    .isCheckedIn(false)
                    .build();

            when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(REG_ID, CLAN_ID))
                    .thenReturn(Optional.of(reg));
            when(registrationMemberDetailRepository.findAllByRegistrationId(REG_ID))
                    .thenReturn(List.of(detail));

            RegistrationResponse response = registrationService.getRegistrationById(EVENT_ID, REG_ID);

            assertNotNull(response);
            assertEquals(REG_ID, response.registrationId());
            assertEquals("Nguyễn Văn Đại Diện", response.representativeMemberName());
            assertEquals(1, response.attendingMembers().size());
            assertEquals("Nguyễn Văn Con Cả", response.attendingMembers().get(0).fullName());
        }
    }

    @Nested
    @DisplayName("getMyRegistration Tests")
    class GetMyRegistrationTests {

        @Test
        @DisplayName("Should throw BadRequestException when current member ID is not available")
        void getMyRegistration_noCurrentMemberId() {
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.empty());

            assertThrows(BadRequestException.class, () ->
                    registrationService.getMyRegistration(EVENT_ID));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when household has not registered")
        void getMyRegistration_notFound() {
            when(familyRegistrationRepository.findByEventIdAndRepresentativeMemberIdAndEvent_ClanId(
                    EVENT_ID, REP_MEMBER_ID, CLAN_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    registrationService.getMyRegistration(EVENT_ID));
        }

        @Test
        @DisplayName("Should return my registration successfully")
        void getMyRegistration_success() {
            FamilyRegistration reg = FamilyRegistration.builder()
                    .registrationId(REG_ID)
                    .eventId(EVENT_ID)
                    .representativeMemberId(REP_MEMBER_ID)
                    .totalAttendees(3)
                    .vegetarianMeals(1)
                    .savoryMeals(2)
                    .registrationDate(Instant.now())
                    .status(RegistrationStatus.Confirmed)
                    .build();

            when(familyRegistrationRepository.findByEventIdAndRepresentativeMemberIdAndEvent_ClanId(
                    EVENT_ID, REP_MEMBER_ID, CLAN_ID)).thenReturn(Optional.of(reg));
            when(registrationMemberDetailRepository.findAllByRegistrationId(REG_ID)).thenReturn(List.of());

            RegistrationResponse response = registrationService.getMyRegistration(EVENT_ID);

            assertNotNull(response);
            assertEquals(REG_ID, response.registrationId());
            assertEquals(REP_MEMBER_ID, response.representativeMemberId());
        }
    }

    @Nested
    @DisplayName("requestModification Tests")
    class RequestModificationTests {

        private FamilyRegistration existingReg;

        @BeforeEach
        void setUpRegistration() {
            existingReg = FamilyRegistration.builder()
                    .registrationId(REG_ID)
                    .eventId(EVENT_ID)
                    .representativeMemberId(REP_MEMBER_ID)
                    .totalAttendees(5)
                    .vegetarianMeals(1)
                    .savoryMeals(4)
                    .dietaryRequirements("1 chay")
                    .transportationNotes("Xe bus họ tộc")
                    .status(RegistrationStatus.Confirmed)
                    .build();

            when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(REG_ID, CLAN_ID))
                    .thenReturn(Optional.of(existingReg));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when event is not RegistrationOpen")
        void requestModification_eventNotRegistrationOpen() {
            activeEvent.setStatus(EventStatus.Ongoing);

            RequestModifyRegistrationRequest request = new RequestModifyRegistrationRequest(
                    3, 0, 3, null, null, "Bớt người tham gia"
            );

            assertThrows(BusinessRuleException.class, () ->
                    registrationService.requestModification(EVENT_ID, REG_ID, request));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when caller is not owner nor organizer")
        void requestModification_unauthorizedCaller() {
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("OTHER_MEM"));
            when(currentUserProvider.hasAuthority(anyString())).thenReturn(false);
            when(eventPersonnelRepository.existsByEventIdAndMemberIdAndEventRole(anyString(), anyString(), any()))
                    .thenReturn(false);

            RequestModifyRegistrationRequest request = new RequestModifyRegistrationRequest(
                    3, 0, 3, null, null, "Bớt người"
            );

            assertThrows(AccessDeniedException.class, () ->
                    registrationService.requestModification(EVENT_ID, REG_ID, request));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when registration is already cancelled")
        void requestModification_alreadyCancelled() {
            existingReg.setStatus(RegistrationStatus.Cancelled);

            RequestModifyRegistrationRequest request = new RequestModifyRegistrationRequest(
                    3, 0, 3, null, null, "Bớt người"
            );

            assertThrows(BusinessRuleException.class, () ->
                    registrationService.requestModification(EVENT_ID, REG_ID, request));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when proposed meals exceed proposed attendees")
        void requestModification_proposedMealsExceedProposedAttendees() {
            RequestModifyRegistrationRequest request = new RequestModifyRegistrationRequest(
                    2, 2, 1, null, null, "Đổi món" // meals = 3 > 2
            );

            assertThrows(BusinessRuleException.class, () ->
                    registrationService.requestModification(EVENT_ID, REG_ID, request));
        }

        @Test
        @DisplayName("Should transition status to PendingModification and publish domain event")
        void requestModification_success() {
            RequestModifyRegistrationRequest request = new RequestModifyRegistrationRequest(
                    3, 0, 3, "Đổi sang toàn bộ cỗ mặn", "Đi xe máy riêng", "Hai cháu bận thi học kỳ"
            );

            RegistrationResponse response = registrationService.requestModification(EVENT_ID, REG_ID, request);

            assertNotNull(response);
            assertEquals(RegistrationStatus.PendingModification, response.status());
            assertEquals(3, response.proposedTotalAttendees());
            assertEquals(0, response.proposedVegetarianMeals());
            assertEquals(3, response.proposedSavoryMeals());
            assertEquals("Hai cháu bận thi học kỳ", response.modificationReason());

            ArgumentCaptor<RegistrationModificationRequestedDomainEvent> eventCaptor =
                    ArgumentCaptor.forClass(RegistrationModificationRequestedDomainEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            RegistrationModificationRequestedDomainEvent event = eventCaptor.getValue();
            assertEquals(REG_ID, event.registrationId());
            assertEquals(EVENT_ID, event.eventId());
            assertEquals("MODIFICATION", event.modificationType());
            assertEquals("Hai cháu bận thi học kỳ", event.reason());
        }
    }

    @Nested
    @DisplayName("requestCancellation Tests")
    class RequestCancellationTests {

        private FamilyRegistration existingReg;

        @BeforeEach
        void setUpRegistration() {
            existingReg = FamilyRegistration.builder()
                    .registrationId(REG_ID)
                    .eventId(EVENT_ID)
                    .representativeMemberId(REP_MEMBER_ID)
                    .totalAttendees(4)
                    .vegetarianMeals(0)
                    .savoryMeals(4)
                    .status(RegistrationStatus.Confirmed)
                    .build();

            when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(REG_ID, CLAN_ID))
                    .thenReturn(Optional.of(existingReg));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when registration is already cancelled")
        void requestCancellation_alreadyCancelled() {
            existingReg.setStatus(RegistrationStatus.Cancelled);

            RequestCancelRegistrationRequest request = new RequestCancelRegistrationRequest("Gia đình có việc đột xuất");

            assertThrows(BusinessRuleException.class, () ->
                    registrationService.requestCancellation(EVENT_ID, REG_ID, request));
        }

        @Test
        @DisplayName("Should transition status to PendingCancellation and publish domain event")
        void requestCancellation_success() {
            RequestCancelRegistrationRequest request = new RequestCancelRegistrationRequest("Gia đình phải đi công tác xa");

            RegistrationResponse response = registrationService.requestCancellation(EVENT_ID, REG_ID, request);

            assertNotNull(response);
            assertEquals(RegistrationStatus.PendingCancellation, response.status());
            assertEquals("Gia đình phải đi công tác xa", response.modificationReason());

            ArgumentCaptor<RegistrationModificationRequestedDomainEvent> eventCaptor =
                    ArgumentCaptor.forClass(RegistrationModificationRequestedDomainEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            RegistrationModificationRequestedDomainEvent event = eventCaptor.getValue();
            assertEquals("CANCELLATION", event.modificationType());
            assertEquals("Gia đình phải đi công tác xa", event.reason());
        }
    }

    @Nested
    @DisplayName("reviewRegistrationRequest Tests")
    class ReviewRegistrationRequestTests {

        private FamilyRegistration pendingModReg;
        private FamilyRegistration pendingCancelReg;

        @BeforeEach
        void setUpRegistrations() {
            pendingModReg = FamilyRegistration.builder()
                    .registrationId(REG_ID)
                    .eventId(EVENT_ID)
                    .representativeMemberId(REP_MEMBER_ID)
                    .totalAttendees(5)
                    .vegetarianMeals(1)
                    .savoryMeals(4)
                    .dietaryRequirements("1 chay")
                    .transportationNotes("Xe riêng")
                    .status(RegistrationStatus.PendingModification)
                    .proposedTotalAttendees(3)
                    .proposedVegetarianMeals(0)
                    .proposedSavoryMeals(3)
                    .proposedDietaryRequirements("3 mặn")
                    .proposedTransportationNotes("Đi xe chung")
                    .modificationReason("Giảm bớt người do bận học")
                    .build();

            pendingCancelReg = FamilyRegistration.builder()
                    .registrationId("REG_CANCEL_01")
                    .eventId(EVENT_ID)
                    .representativeMemberId(REP_MEMBER_ID)
                    .totalAttendees(4)
                    .vegetarianMeals(0)
                    .savoryMeals(4)
                    .status(RegistrationStatus.PendingCancellation)
                    .modificationReason("Bận việc hiếu")
                    .build();

            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(true);
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when event is closed")
        void review_eventClosed() {
            activeEvent.setStatus(EventStatus.Closed);

            ReviewRegistrationRequest request = new ReviewRegistrationRequest(true, null);

            assertThrows(BusinessRuleException.class, () ->
                    registrationService.reviewRegistrationRequest(EVENT_ID, REG_ID, request));
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when caller is not privileged organizer")
        void review_callerNotPrivileged() {
            when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
            when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
            when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("REGULAR_MEM"));
            when(eventPersonnelRepository.existsByEventIdAndMemberIdAndEventRole(
                    EVENT_ID, "REGULAR_MEM", EventRole.HeadOfOrganizer)).thenReturn(false);

            ReviewRegistrationRequest request = new ReviewRegistrationRequest(true, null);

            assertThrows(AccessDeniedException.class, () ->
                    registrationService.reviewRegistrationRequest(EVENT_ID, REG_ID, request));
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when registration is not in pending review status")
        void review_notPendingReview() {
            pendingModReg.setStatus(RegistrationStatus.Confirmed);
            when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(REG_ID, CLAN_ID))
                    .thenReturn(Optional.of(pendingModReg));

            ReviewRegistrationRequest request = new ReviewRegistrationRequest(true, null);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                    registrationService.reviewRegistrationRequest(EVENT_ID, REG_ID, request));
            assertTrue(ex.getMessage().contains("không có yêu cầu sửa đổi hoặc hủy nào đang chờ duyệt"));
        }

        @Test
        @DisplayName("Should approve modification and update active numbers to proposed values")
        void review_approveModification() {
            when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(REG_ID, CLAN_ID))
                    .thenReturn(Optional.of(pendingModReg));

            ReviewRegistrationRequest request = new ReviewRegistrationRequest(true, null);

            RegistrationResponse response = registrationService.reviewRegistrationRequest(EVENT_ID, REG_ID, request);

            assertNotNull(response);
            assertEquals(RegistrationStatus.Confirmed, response.status());
            assertEquals(3, response.totalAttendees());
            assertEquals(0, response.vegetarianMeals());
            assertEquals(3, response.savoryMeals());
            assertEquals("3 mặn", response.dietaryRequirements());
            assertEquals("Đi xe chung", response.transportationNotes());
            assertNull(response.rejectionReason());

            ArgumentCaptor<RegistrationReviewedDomainEvent> eventCaptor =
                    ArgumentCaptor.forClass(RegistrationReviewedDomainEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            RegistrationReviewedDomainEvent event = eventCaptor.getValue();
            assertTrue(event.approved());
            assertEquals(REG_ID, event.registrationId());
        }

        @Test
        @DisplayName("Should approve cancellation and transition status to Cancelled")
        void review_approveCancellation() {
            when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId("REG_CANCEL_01", CLAN_ID))
                    .thenReturn(Optional.of(pendingCancelReg));

            ReviewRegistrationRequest request = new ReviewRegistrationRequest(true, null);

            RegistrationResponse response = registrationService.reviewRegistrationRequest(EVENT_ID, "REG_CANCEL_01", request);

            assertNotNull(response);
            assertEquals(RegistrationStatus.Cancelled, response.status());

            ArgumentCaptor<RegistrationReviewedDomainEvent> eventCaptor =
                    ArgumentCaptor.forClass(RegistrationReviewedDomainEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            RegistrationReviewedDomainEvent event = eventCaptor.getValue();
            assertTrue(event.approved());
            assertEquals("REG_CANCEL_01", event.registrationId());
        }

        @Test
        @DisplayName("Should throw BadRequestException when rejecting without rejection reason")
        void review_rejectWithoutReason() {
            when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(REG_ID, CLAN_ID))
                    .thenReturn(Optional.of(pendingModReg));

            ReviewRegistrationRequest request = new ReviewRegistrationRequest(false, "   ");

            assertThrows(BadRequestException.class, () ->
                    registrationService.reviewRegistrationRequest(EVENT_ID, REG_ID, request));
        }

        @Test
        @DisplayName("Should reject modification, keep original numbers, set rejectionReason and return Confirmed")
        void review_rejectWithReason() {
            when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(REG_ID, CLAN_ID))
                    .thenReturn(Optional.of(pendingModReg));

            ReviewRegistrationRequest request = new ReviewRegistrationRequest(
                    false, "Ban tổ chức đã chốt đặt cọc và mua nguyên liệu cho 5 suất ăn"
            );

            RegistrationResponse response = registrationService.reviewRegistrationRequest(EVENT_ID, REG_ID, request);

            assertNotNull(response);
            assertEquals(RegistrationStatus.Confirmed, response.status());
            assertEquals(5, response.totalAttendees()); // keeps original
            assertEquals(1, response.vegetarianMeals());
            assertEquals(4, response.savoryMeals());
            assertEquals("Ban tổ chức đã chốt đặt cọc và mua nguyên liệu cho 5 suất ăn", response.rejectionReason());

            ArgumentCaptor<RegistrationReviewedDomainEvent> eventCaptor =
                    ArgumentCaptor.forClass(RegistrationReviewedDomainEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            RegistrationReviewedDomainEvent event = eventCaptor.getValue();
            assertFalse(event.approved());
            assertEquals("Ban tổ chức đã chốt đặt cọc và mua nguyên liệu cho 5 suất ăn", event.rejectionReason());
        }
    }
}
