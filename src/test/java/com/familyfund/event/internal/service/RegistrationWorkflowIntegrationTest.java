package com.familyfund.event.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.DuplicateResourceException;
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
import com.familyfund.event.internal.entity.EventPersonnel;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * End-to-End Workflow Integration Test for US-06: Phân hệ Đăng ký tham dự sự kiện.
 * Verifies the complete lifecycle and real-time aggregation across multiple households:
 * 1. Initial State & Real-time Aggregation (0 attendees).
 * 2. Household A registers (4 attendees, 1 veg, 3 savory, 2 attending members) -> Confirmed, Event emitted.
 * 3. Real-time Aggregation updates (4 attendees).
 * 4. Household B registers (6 attendees, 2 veg, 4 savory) -> Confirmed.
 * 5. Real-time Aggregation updates (10 attendees, 3 veg, 7 savory).
 * 6. Duplicate Registration attempt blocked.
 * 7. Modification Workflow: Household A proposes decrease to 2 attendees -> PendingModification.
 * 8. BTC Review & Approval: Active numbers updated to 2, Aggregation updates to 8 attendees.
 * 9. Cancellation Workflow: Household B proposes cancellation -> PendingCancellation.
 * 10. BTC Review & Approval: Status -> Cancelled, Aggregation drops to 2 attendees.
 * 11. Rejection Workflow: Household A proposes change, BTC rejects with reason -> Confirmed with rejection reason.
 * 12. Closed Event Lockout: Actions on closed event rejected.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RegistrationWorkflowIntegrationTest")
class RegistrationWorkflowIntegrationTest {

    private static final String CLAN_ID = "CLAN_NGUYEN_01";
    private static final String EVENT_ID = "EVT_GIO_TO_2026";
    private static final String REP_A = "MEM_NGUYEN_VAN_A";
    private static final String REP_B = "MEM_NGUYEN_VAN_B";
    private static final String ATTENDEE_A1 = "MEM_NGUYEN_VAN_CON_1";
    private static final String ATTENDEE_A2 = "MEM_NGUYEN_VAN_CON_2";
    private static final String ORGANIZER_HEAD = "MEM_TRUONG_BTC";

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

    private RegistrationIdGenerator registrationIdGenerator;
    private RegistrationMapper registrationMapper;
    private RegistrationServiceImpl registrationService;

    // In-memory backing storage to simulate multi-step realistic persistence
    private final Map<String, FamilyRegistration> registrationStore = new LinkedHashMap<>();
    private final Map<String, List<RegistrationMemberDetail>> memberDetailStore = new HashMap<>();
    private final List<Object> publishedEvents = new ArrayList<>();

    private Event activeEvent;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);

        registrationMapper = Mappers.getMapper(RegistrationMapper.class);
        registrationIdGenerator = new RegistrationIdGenerator();

        ApplicationEventPublisher eventPublisher = publishedEvents::add;

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

        activeEvent = Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Đại Lễ Giỗ Tổ Họ Nguyễn 2026")
                .eventDatetime(Instant.parse("2026-11-20T08:00:00Z"))
                .status(EventStatus.RegistrationOpen)
                .build();

        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(activeEvent));
        when(eventRepository.existsByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(true);

        // Genealogy mock data
        when(genealogyQueryService.findMemberSummary(REP_A))
                .thenReturn(Optional.of(new MemberSummaryDto(REP_A, "Nguyễn Văn A", CLAN_ID, "BR_01")));
        when(genealogyQueryService.findMemberSummary(REP_B))
                .thenReturn(Optional.of(new MemberSummaryDto(REP_B, "Nguyễn Văn B", CLAN_ID, "BR_02")));
        when(genealogyQueryService.findMemberSummary(ATTENDEE_A1))
                .thenReturn(Optional.of(new MemberSummaryDto(ATTENDEE_A1, "Nguyễn Văn Con 1", CLAN_ID, "BR_01")));
        when(genealogyQueryService.findMemberSummary(ATTENDEE_A2))
                .thenReturn(Optional.of(new MemberSummaryDto(ATTENDEE_A2, "Nguyễn Văn Con 2", CLAN_ID, "BR_01")));
        when(genealogyQueryService.findMemberSummary(ORGANIZER_HEAD))
                .thenReturn(Optional.of(new MemberSummaryDto(ORGANIZER_HEAD, "Trưởng Ban Tổ Chức", CLAN_ID, "BR_01")));

        // Repository save behavior
        when(familyRegistrationRepository.save(any(FamilyRegistration.class))).thenAnswer(invocation -> {
            FamilyRegistration reg = invocation.getArgument(0);
            registrationStore.put(reg.getRegistrationId(), reg);
            return reg;
        });

        // Repository duplicate check
        when(familyRegistrationRepository.existsByEventIdAndRepresentativeMemberIdAndStatusNot(
                eq(EVENT_ID), anyString(), eq(RegistrationStatus.Cancelled))).thenAnswer(invocation -> {
            String repId = invocation.getArgument(1);
            return registrationStore.values().stream()
                    .anyMatch(r -> r.getEventId().equals(EVENT_ID)
                            && r.getRepresentativeMemberId().equals(repId)
                            && r.getStatus() != RegistrationStatus.Cancelled);
        });

        // Repository find by registrationId
        when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(anyString(), eq(CLAN_ID)))
                .thenAnswer(invocation -> {
                    String regId = invocation.getArgument(0);
                    return Optional.ofNullable(registrationStore.get(regId));
                });

        // Repository details save and find
        when(registrationMemberDetailRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<RegistrationMemberDetail> list = invocation.getArgument(0);
            for (RegistrationMemberDetail detail : list) {
                memberDetailStore.computeIfAbsent(detail.getRegistrationId(), k -> new ArrayList<>()).add(detail);
            }
            return list;
        });
        when(registrationMemberDetailRepository.findAllByRegistrationId(anyString())).thenAnswer(invocation -> {
            String regId = invocation.getArgument(0);
            return memberDetailStore.getOrDefault(regId, Collections.emptyList());
        });

        // Repository aggregate summary projection
        when(familyRegistrationRepository.getAggregateSummary(EVENT_ID, CLAN_ID)).thenAnswer(invocation -> {
            List<FamilyRegistration> active = registrationStore.values().stream()
                    .filter(r -> r.getEventId().equals(EVENT_ID) && r.getStatus() != RegistrationStatus.Cancelled)
                    .toList();

            long totalRegs = active.size();
            long totalAttendees = active.stream().mapToLong(FamilyRegistration::getTotalAttendees).sum();
            long totalVeg = active.stream().mapToLong(FamilyRegistration::getVegetarianMeals).sum();
            long totalSavory = active.stream().mapToLong(FamilyRegistration::getSavoryMeals).sum();

            return new RegistrationAggregateProjection() {
                @Override
                public long getTotalRegistrations() {
                    return totalRegs;
                }

                @Override
                public long getTotalAttendees() {
                    return totalAttendees;
                }

                @Override
                public long getTotalVegetarianMeals() {
                    return totalVeg;
                }

                @Override
                public long getTotalSavoryMeals() {
                    return totalSavory;
                }
            };
        });

        // Organizer role mock
        when(eventPersonnelRepository.existsByEventIdAndMemberIdAndEventRole(
                EVENT_ID, ORGANIZER_HEAD, EventRole.HeadOfOrganizer)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        registrationStore.clear();
        memberDetailStore.clear();
        publishedEvents.clear();
    }

    @Test
    @DisplayName("Complete US-06 Lifecycle: Register -> Real-time Aggregation -> Modify -> Review -> Cancel -> Rejection -> Closed Lockout")
    void completeEventRegistrationLifecycle() {
        // =========================================================================
        // Step 1: Initial state - 0 registrations, 0 attendees
        // =========================================================================
        RegistrationSummaryResponse initialSummary = registrationService.getRegistrationSummary(EVENT_ID);
        assertThat(initialSummary.totalRegistrations()).isEqualTo(0);
        assertThat(initialSummary.totalAttendees()).isEqualTo(0);
        assertThat(initialSummary.totalVegetarianMeals()).isEqualTo(0);
        assertThat(initialSummary.totalSavoryMeals()).isEqualTo(0);

        // =========================================================================
        // Step 2: Household A registers (4 attendees: 1 veg, 3 savory, 2 accompanying members)
        // =========================================================================
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(REP_A));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_A_01"));

        RegisterAttendeesRequest reqA = new RegisterAttendeesRequest(
                null,
                4,
                1,
                3,
                "1 suất chay thanh tịnh",
                "Gia đình tự túc 1 xe ô tô 4 chỗ",
                List.of(ATTENDEE_A1, ATTENDEE_A2)
        );

        RegistrationResponse respA = registrationService.registerAttendees(EVENT_ID, reqA);
        assertNotNull(respA);
        assertThat(respA.registrationId()).startsWith(RegistrationIdGenerator.PREFIX);
        assertThat(respA.representativeMemberName()).isEqualTo("Nguyễn Văn A");
        assertThat(respA.totalAttendees()).isEqualTo(4);
        assertThat(respA.vegetarianMeals()).isEqualTo(1);
        assertThat(respA.savoryMeals()).isEqualTo(3);
        assertThat(respA.status()).isEqualTo(RegistrationStatus.Confirmed);
        assertThat(respA.attendingMembers()).hasSize(2);

        // Verify Domain Event emitted for Household A
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(FamilyRegistrationSubmittedDomainEvent.class);
        FamilyRegistrationSubmittedDomainEvent eventA = (FamilyRegistrationSubmittedDomainEvent) publishedEvents.get(0);
        assertThat(eventA.representativeMemberId()).isEqualTo(REP_A);
        assertThat(eventA.totalAttendees()).isEqualTo(4);

        // =========================================================================
        // Step 3: Aggregation after Household A
        // =========================================================================
        RegistrationSummaryResponse summaryAfterA = registrationService.getRegistrationSummary(EVENT_ID);
        assertThat(summaryAfterA.totalRegistrations()).isEqualTo(1);
        assertThat(summaryAfterA.totalAttendees()).isEqualTo(4);
        assertThat(summaryAfterA.totalVegetarianMeals()).isEqualTo(1);
        assertThat(summaryAfterA.totalSavoryMeals()).isEqualTo(3);

        // =========================================================================
        // Step 4: Household B registers (6 attendees: 2 veg, 4 savory)
        // =========================================================================
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(REP_B));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_B_02"));

        RegisterAttendeesRequest reqB = new RegisterAttendeesRequest(
                null,
                6,
                2,
                4,
                "2 suất ăn chay",
                "Đi cùng xe khách dòng họ",
                null
        );

        RegistrationResponse respB = registrationService.registerAttendees(EVENT_ID, reqB);
        assertThat(respB.totalAttendees()).isEqualTo(6);
        assertThat(respB.status()).isEqualTo(RegistrationStatus.Confirmed);

        // =========================================================================
        // Step 5: Aggregation after Household B (Total = 10 attendees, 3 veg, 7 savory)
        // =========================================================================
        RegistrationSummaryResponse summaryAfterB = registrationService.getRegistrationSummary(EVENT_ID);
        assertThat(summaryAfterB.totalRegistrations()).isEqualTo(2);
        assertThat(summaryAfterB.totalAttendees()).isEqualTo(10);
        assertThat(summaryAfterB.totalVegetarianMeals()).isEqualTo(3);
        assertThat(summaryAfterB.totalSavoryMeals()).isEqualTo(7);

        // =========================================================================
        // Step 6: Prevent duplicate registration for Household A
        // =========================================================================
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(REP_A));
        assertThrows(DuplicateResourceException.class, () ->
                registrationService.registerAttendees(EVENT_ID, reqA));

        // =========================================================================
        // Step 7: Household A requests modification (proposes 2 attendees, 0 veg, 2 savory)
        // =========================================================================
        RequestModifyRegistrationRequest modReq = new RequestModifyRegistrationRequest(
                2,
                0,
                2,
                "Chuyển toàn bộ cỗ mặn",
                "Đi 1 xe máy riêng",
                "Hai cháu bận thi học kỳ"
        );

        RegistrationResponse modResponse = registrationService.requestModification(EVENT_ID, respA.registrationId(), modReq);
        assertThat(modResponse.status()).isEqualTo(RegistrationStatus.PendingModification);
        assertThat(modResponse.proposedTotalAttendees()).isEqualTo(2);
        assertThat(modResponse.modificationReason()).isEqualTo("Hai cháu bận thi học kỳ");

        // While pending, active numbers must remain unchanged in the aggregate summary
        RegistrationSummaryResponse summaryPending = registrationService.getRegistrationSummary(EVENT_ID);
        assertThat(summaryPending.totalAttendees()).isEqualTo(10); // Still 4 + 6

        // =========================================================================
        // Step 8: BTC Reviews and Approves Household A's Modification
        // =========================================================================
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(ORGANIZER_HEAD));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_BTC_01"));

        ReviewRegistrationRequest approveModReq = new ReviewRegistrationRequest(true, null);
        RegistrationResponse reviewedModResponse =
                registrationService.reviewRegistrationRequest(EVENT_ID, respA.registrationId(), approveModReq);

        assertThat(reviewedModResponse.status()).isEqualTo(RegistrationStatus.Confirmed);
        assertThat(reviewedModResponse.totalAttendees()).isEqualTo(2); // Updated to proposed
        assertThat(reviewedModResponse.vegetarianMeals()).isEqualTo(0);
        assertThat(reviewedModResponse.savoryMeals()).isEqualTo(2);
        assertThat(reviewedModResponse.dietaryRequirements()).isEqualTo("Chuyển toàn bộ cỗ mặn");

        // Aggregation reflects modified attendees in real-time (2 + 6 = 8 attendees)
        RegistrationSummaryResponse summaryAfterMod = registrationService.getRegistrationSummary(EVENT_ID);
        assertThat(summaryAfterMod.totalRegistrations()).isEqualTo(2);
        assertThat(summaryAfterMod.totalAttendees()).isEqualTo(8);
        assertThat(summaryAfterMod.totalVegetarianMeals()).isEqualTo(2);
        assertThat(summaryAfterMod.totalSavoryMeals()).isEqualTo(6);

        // =========================================================================
        // Step 9: Household B requests cancellation
        // =========================================================================
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(REP_B));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_B_02"));

        RequestCancelRegistrationRequest cancelReq =
                new RequestCancelRegistrationRequest("Gia đình có việc hiếu đột xuất ở quê");
        RegistrationResponse cancelReqResponse =
                registrationService.requestCancellation(EVENT_ID, respB.registrationId(), cancelReq);

        assertThat(cancelReqResponse.status()).isEqualTo(RegistrationStatus.PendingCancellation);
        assertThat(cancelReqResponse.modificationReason()).isEqualTo("Gia đình có việc hiếu đột xuất ở quê");

        // =========================================================================
        // Step 10: BTC Reviews and Approves Household B's Cancellation
        // =========================================================================
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(ORGANIZER_HEAD));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_BTC_01"));

        ReviewRegistrationRequest approveCancelReq = new ReviewRegistrationRequest(true, null);
        RegistrationResponse cancelledResponse =
                registrationService.reviewRegistrationRequest(EVENT_ID, respB.registrationId(), approveCancelReq);

        assertThat(cancelledResponse.status()).isEqualTo(RegistrationStatus.Cancelled);

        // Aggregation completely excludes cancelled registration: only Household A (2 attendees)
        RegistrationSummaryResponse summaryAfterCancel = registrationService.getRegistrationSummary(EVENT_ID);
        assertThat(summaryAfterCancel.totalRegistrations()).isEqualTo(1);
        assertThat(summaryAfterCancel.totalAttendees()).isEqualTo(2);
        assertThat(summaryAfterCancel.totalVegetarianMeals()).isEqualTo(0);
        assertThat(summaryAfterCancel.totalSavoryMeals()).isEqualTo(2);

        // =========================================================================
        // Step 11: Rejection Workflow: Household A proposes change, BTC rejects with reason
        // =========================================================================
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(REP_A));
        RequestModifyRegistrationRequest anotherMod = new RequestModifyRegistrationRequest(
                5, 2, 3, null, null, "Muốn tăng thêm người tham dự"
        );
        registrationService.requestModification(EVENT_ID, respA.registrationId(), anotherMod);

        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(ORGANIZER_HEAD));
        ReviewRegistrationRequest rejectReq = new ReviewRegistrationRequest(
                false, "Ban tổ chức đã chốt đặt cọc cỗ và không thể tăng thêm lúc này"
        );
        RegistrationResponse rejectedResponse =
                registrationService.reviewRegistrationRequest(EVENT_ID, respA.registrationId(), rejectReq);

        assertThat(rejectedResponse.status()).isEqualTo(RegistrationStatus.Confirmed);
        assertThat(rejectedResponse.totalAttendees()).isEqualTo(2); // Retains original 2 attendees
        assertThat(rejectedResponse.rejectionReason()).isEqualTo("Ban tổ chức đã chốt đặt cọc cỗ và không thể tăng thêm lúc này");

        // =========================================================================
        // Step 12: Closed Event Lockout
        // =========================================================================
        activeEvent.setStatus(EventStatus.Closed);

        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(REP_A));
        RequestModifyRegistrationRequest lateMod = new RequestModifyRegistrationRequest(
                1, 0, 1, null, null, "Sửa muộn"
        );
        assertThrows(BusinessRuleException.class, () ->
                registrationService.requestModification(EVENT_ID, respA.registrationId(), lateMod));

        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(ORGANIZER_HEAD));
        assertThrows(BusinessRuleException.class, () ->
                registrationService.reviewRegistrationRequest(EVENT_ID, respA.registrationId(), approveModReq));
    }
}

