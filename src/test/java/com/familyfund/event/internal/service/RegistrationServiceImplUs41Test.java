package com.familyfund.event.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.RegistrationStatus;
import com.familyfund.event.internal.dto.request.UpdateRegistrationRequest;
import com.familyfund.event.internal.dto.response.RegistrationResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.FamilyRegistration;
import com.familyfund.event.internal.mapper.RegistrationMapper;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.FamilyRegistrationRepository;
import com.familyfund.event.internal.repository.RegistrationMemberDetailRepository;
import com.familyfund.event.internal.util.RegistrationIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("US-41: RegistrationServiceImpl Update & Cancel Before Close Tests")
class RegistrationServiceImplUs41Test {

    private static final String CLAN_ID = "CLAN_01";
    private static final String EVENT_ID = "EVT_01";
    private static final String REG_ID = "REG_01";
    private static final String REP_MEMBER_ID = "MEM_REP";

    @Mock private EventRepository eventRepository;
    @Mock private FamilyRegistrationRepository familyRegistrationRepository;
    @Mock private RegistrationMemberDetailRepository registrationMemberDetailRepository;
    @Mock private EventPersonnelRepository eventPersonnelRepository;
    @Mock private GenealogyQueryService genealogyQueryService;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private RegistrationIdGenerator registrationIdGenerator;
    @Mock private RegistrationMapper registrationMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RegistrationServiceImpl service;

    private FamilyRegistration registration;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        registration = FamilyRegistration.builder()
                .registrationId(REG_ID)
                .eventId(EVENT_ID)
                .representativeMemberId(REP_MEMBER_ID)
                .totalAttendees(3)
                .vegetarianMeals(1)
                .savoryMeals(2)
                .status(RegistrationStatus.Confirmed)
                .build();

        lenient().when(familyRegistrationRepository.findByRegistrationIdAndEvent_ClanId(REG_ID, CLAN_ID))
                .thenReturn(Optional.of(registration));
        lenient().when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(REP_MEMBER_ID));
        lenient().when(genealogyQueryService.findMemberSummary(REP_MEMBER_ID))
                .thenReturn(Optional.of(new MemberSummaryDto(REP_MEMBER_ID, "Nguyễn Văn A", CLAN_ID, "BR_01")));
        lenient().when(registrationMemberDetailRepository.findAllByRegistrationId(REG_ID))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Cập nhật số lượng thành công khi sự kiện ở trạng thái RegistrationOpen")
    void updateRegistration_whenOpen_updatesSuccessfully() {
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID))
                .thenReturn(Optional.of(Event.builder()
                        .eventId(EVENT_ID)
                        .clanId(CLAN_ID)
                        .eventName("Giỗ Tổ")
                        .status(EventStatus.RegistrationOpen)
                        .build()));
        when(familyRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(registrationMapper.toResponse(any(), any(), any())).thenAnswer(inv -> {
            FamilyRegistration r = inv.getArgument(0);
            return new RegistrationResponse(
                    r.getRegistrationId(), r.getEventId(), r.getRepresentativeMemberId(), "Nguyễn Văn A",
                    r.getTotalAttendees(), r.getVegetarianMeals(), r.getSavoryMeals(),
                    r.getDietaryRequirements(), r.getTransportationNotes(), r.getRegistrationDate(),
                    r.getStatus(), null, null, null, null, null, null, null, null, null, r.getUpdatedAt(), List.of());
        });

        UpdateRegistrationRequest req = UpdateRegistrationRequest.builder()
                .totalAttendees(5)
                .vegetarianMeals(2)
                .savoryMeals(3)
                .transportationNotes("Xe 7 chỗ")
                .build();

        RegistrationResponse res = service.updateRegistration(REG_ID, req);
        assertThat(res.totalAttendees()).isEqualTo(5);
        assertThat(res.vegetarianMeals()).isEqualTo(2);
        assertThat(res.savoryMeals()).isEqualTo(3);
    }

    @Test
    @DisplayName("Hủy đăng ký thành công khi sự kiện ở trạng thái RegistrationOpen")
    void cancelRegistration_whenOpen_marksCancelled() {
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID))
                .thenReturn(Optional.of(Event.builder()
                        .eventId(EVENT_ID)
                        .clanId(CLAN_ID)
                        .eventName("Giỗ Tổ")
                        .status(EventStatus.RegistrationOpen)
                        .build()));
        when(familyRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(registrationMapper.toResponse(any(), any(), any())).thenAnswer(inv -> {
            FamilyRegistration r = inv.getArgument(0);
            return new RegistrationResponse(
                    r.getRegistrationId(), r.getEventId(), r.getRepresentativeMemberId(), "Nguyễn Văn A",
                    r.getTotalAttendees(), r.getVegetarianMeals(), r.getSavoryMeals(),
                    r.getDietaryRequirements(), r.getTransportationNotes(), r.getRegistrationDate(),
                    r.getStatus(), null, null, null, null, null, null, null, null, null, r.getUpdatedAt(), List.of());
        });

        RegistrationResponse res = service.cancelRegistration(REG_ID);
        assertThat(res.status()).isEqualTo(RegistrationStatus.Cancelled);
    }

    @Test
    @DisplayName("Chặn sửa và hủy khi sự kiện đã chuyển sang RegistrationClosed kèm thông báo liên hệ BTC")
    void updateOrCancel_whenRegistrationClosed_throwsBusinessRuleWithContactMessage() {
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID))
                .thenReturn(Optional.of(Event.builder()
                        .eventId(EVENT_ID)
                        .clanId(CLAN_ID)
                        .eventName("Giỗ Tổ")
                        .status(EventStatus.RegistrationClosed)
                        .build()));

        UpdateRegistrationRequest req = UpdateRegistrationRequest.builder()
                .totalAttendees(4)
                .vegetarianMeals(1)
                .savoryMeals(3)
                .build();

        assertThatThrownBy(() -> service.updateRegistration(REG_ID, req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("liên hệ trực tiếp Ban tổ chức");

        assertThatThrownBy(() -> service.cancelRegistration(REG_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("liên hệ trực tiếp Ban tổ chức");
    }

    @Test
    @DisplayName("Chặn người khác không phải đại diện hộ gia đình hoặc BTC sửa/hủy")
    void updateRegistration_unauthorizedMember_throwsAccessDenied() {
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID))
                .thenReturn(Optional.of(Event.builder()
                        .eventId(EVENT_ID)
                        .clanId(CLAN_ID)
                        .status(EventStatus.RegistrationOpen)
                        .build()));
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_OTHER"));

        UpdateRegistrationRequest req = UpdateRegistrationRequest.builder()
                .totalAttendees(2)
                .build();

        assertThatThrownBy(() -> service.updateRegistration(REG_ID, req))
                .isInstanceOf(AccessDeniedException.class);
    }
}
