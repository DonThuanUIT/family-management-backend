package com.familyfund.event.internal.anniversary;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.DeathAnniversaryDto;
import com.familyfund.event.api.dto.DeathAnniversaryReminderDto;
import com.familyfund.event.internal.entity.DeathAnniversaryReminder;
import com.familyfund.event.internal.repository.DeathAnniversaryReminderRepository;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.LunarCalendarService;
import com.familyfund.genealogy.api.dto.DeceasedMemberDto;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link DeathAnniversaryServiceImpl} (US-40): the scan loop (isolation, tenant clearing), the
 * upcoming-anniversary read side (window, suggestion suppression, ordering) and the reminder history.
 */
@ExtendWith(MockitoExtension.class)
class DeathAnniversaryServiceImplTest {

    private static final String CLAN = "CLAN_GIO_SVC";
    private static final String MEMBER = "MEM_GIO_SVC";
    private static final String OTHER_MEMBER = "MEM_GIO_SVC_2";
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 20);

    @Mock
    private GenealogyQueryService genealogyQueryService;
    @Mock
    private LunarCalendarService lunarCalendarService;
    @Mock
    private DeathAnniversaryReminderRepository reminderRepository;
    @Mock
    private DeathAnniversaryIssuer issuer;
    @Mock
    private DeathAnniversaryCalculator calculator;
    @Mock
    private AnniversaryEventMatcher eventMatcher;

    private DeathAnniversaryServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN);
        // The properties are a plain record: construct the service explicitly instead of relying on @InjectMocks.
        DeathAnniversaryProperties properties = new DeathAnniversaryProperties(
                true, null, "Asia/Ho_Chi_Minh", List.of(14, 3), 30, null, null, null);
        service = new DeathAnniversaryServiceImpl(genealogyQueryService, lunarCalendarService, reminderRepository,
                issuer, calculator, eventMatcher, properties);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private DeceasedMemberDto member(String memberId) {
        return DeceasedMemberDto.builder()
                .memberId(memberId).clanId(CLAN).fullName("Nguyễn Văn " + memberId)
                .generation(3).branchId("BR_1").branchName("Chi Một")
                .deathDateLunar("15/02 Giáp Thìn")
                .build();
    }

    private DeathAnniversaryDto anniversary(String memberId, long daysRemaining, Integer milestone) {
        return DeathAnniversaryDto.builder()
                .memberId(memberId).memberFullName("Nguyễn Văn " + memberId).generation(3)
                .branchId("BR_1").branchName("Chi Một").deathDateLunar("15/02 Giáp Thìn")
                .lunarDay(15).lunarMonth(2).leapMonth(false)
                .anniversaryYear(2026).anniversaryDate(TODAY.plusDays(daysRemaining))
                .daysRemaining(daysRemaining).milestoneDays(milestone)
                .reminderTriggered(false).alreadyReminded(false)
                .build();
    }

    // ------------------------------------------------------------------ scan

    @Test
    @DisplayName("the scan issues one reminder per matching member")
    void scanIssuesPerMember() {
        when(genealogyQueryService.findAllDeceasedMembersWithAnniversary())
                .thenReturn(List.of(member(MEMBER), member(OTHER_MEMBER)));
        when(issuer.issueReminder(any(), any())).thenReturn(true, false);

        int sent = service.scanAndSendReminders(TODAY);

        assertThat(sent).isEqualTo(1);
        verify(issuer).issueReminder(member(MEMBER), TODAY);
        verify(issuer).issueReminder(member(OTHER_MEMBER), TODAY);
    }

    @Test
    @DisplayName("a failing member does not stop the pass")
    void scanIsolatesFailures() {
        when(genealogyQueryService.findAllDeceasedMembersWithAnniversary())
                .thenReturn(List.of(member(MEMBER), member(OTHER_MEMBER)));
        when(issuer.issueReminder(member(MEMBER), TODAY)).thenThrow(new IllegalStateException("boom"));
        when(issuer.issueReminder(member(OTHER_MEMBER), TODAY)).thenReturn(true);

        int sent = service.scanAndSendReminders(TODAY);

        assertThat(sent).isEqualTo(1);
    }

    @Test
    @DisplayName("a scan with no candidate does not touch the issuer")
    void scanWithoutCandidates() {
        when(genealogyQueryService.findAllDeceasedMembersWithAnniversary()).thenReturn(List.of());

        assertThat(service.scanAndSendReminders(TODAY)).isZero();
        verifyNoInteractions(issuer);
    }

    @Test
    @DisplayName("the scan clears the tenant context (it runs outside any request)")
    void scanClearsTenant() {
        when(genealogyQueryService.findAllDeceasedMembersWithAnniversary()).thenReturn(List.of());

        TenantContext.setClanId("CLAN_LEAKED");
        service.scanAndSendReminders(TODAY);

        assertThat(TenantContext.getClanId()).isEqualTo(TenantContext.DEFAULT_CLAN_ID);
    }

    // ------------------------------------------------------------------ upcoming

    @Test
    @DisplayName("upcoming anniversaries keep only the members inside the window")
    void upcomingFiltersByWindow() {
        when(genealogyQueryService.findDeceasedMembersWithAnniversary(CLAN))
                .thenReturn(List.of(member(MEMBER), member(OTHER_MEMBER)));
        when(calculator.compute(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(Optional.of(anniversary(MEMBER, 14, 14)))
                .thenReturn(Optional.of(anniversary(OTHER_MEMBER, 40, null)));
        when(eventMatcher.isCoveredByExistingEvent(anyString(), any(), any(), any())).thenReturn(false);
        when(reminderRepository.existsByMemberIdAndLunarYearAndMilestoneDays(MEMBER, 2026, 14)).thenReturn(true);

        List<DeathAnniversaryDto> result = service.upcomingAnniversaries(TODAY, 30);

        assertThat(result).hasSize(1);
        DeathAnniversaryDto dto = result.getFirst();
        assertThat(dto.memberId()).isEqualTo(MEMBER);
        assertThat(dto.alreadyReminded()).isTrue();
        assertThat(dto.reminderTriggered()).isTrue();
        assertThat(dto.milestoneDays()).isEqualTo(14);
        assertThat(dto.daysRemaining()).isEqualTo(14);
    }

    @Test
    @DisplayName("an anniversary already covered by an event loses its suggestion")
    void upcomingSuppressesCoveredSuggestion() {
        DeathAnniversaryDto withSuggestion = DeathAnniversaryDto.builder()
                .memberId(MEMBER).memberFullName("Nguyễn Văn An").anniversaryYear(2026)
                .anniversaryDate(TODAY.plusDays(14)).daysRemaining(14).milestoneDays(14)
                .suggestion(com.familyfund.event.api.dto.EventSuggestionDto.builder()
                        .suggestionId("GSU_1").eventName("Đám giỗ").build())
                .build();
        when(genealogyQueryService.findDeceasedMembersWithAnniversary(CLAN)).thenReturn(List.of(member(MEMBER)));
        when(calculator.compute(any(), any(), any(), any(), anyBoolean())).thenReturn(Optional.of(withSuggestion));
        when(eventMatcher.isCoveredByExistingEvent(anyString(), any(), any(), any())).thenReturn(true);

        List<DeathAnniversaryDto> result = service.upcomingAnniversaries(TODAY, 30);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().suggestion()).isNull();
        assertThat(result.getFirst().milestoneDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("anniversaries are ordered by date")
    void upcomingIsSortedByDate() {
        when(genealogyQueryService.findDeceasedMembersWithAnniversary(CLAN))
                .thenReturn(List.of(member(OTHER_MEMBER), member(MEMBER)));
        when(calculator.compute(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(Optional.of(anniversary(OTHER_MEMBER, 20, null)))
                .thenReturn(Optional.of(anniversary(MEMBER, 5, null)));
        when(eventMatcher.isCoveredByExistingEvent(anyString(), any(), any(), any())).thenReturn(false);

        List<DeathAnniversaryDto> result = service.upcomingAnniversaries(TODAY, 30);

        assertThat(result).extracting(DeathAnniversaryDto::daysRemaining).containsExactly(5L, 20L);
    }

    @Test
    @DisplayName("unresolvable anniversaries are dropped from the read side")
    void upcomingDropsUnresolvable() {
        when(genealogyQueryService.findDeceasedMembersWithAnniversary(CLAN)).thenReturn(List.of(member(MEMBER)));
        when(calculator.compute(any(), any(), any(), any(), anyBoolean())).thenReturn(Optional.empty());

        assertThat(service.upcomingAnniversaries(TODAY, 30)).isEmpty();
        verify(eventMatcher, never()).isCoveredByExistingEvent(anyString(), any(), any(), any());
    }

    // ------------------------------------------------------------------ reminders of a member

    @Test
    @DisplayName("reminders of a member are returned newest year first, then by milestone")
    void remindersOfMemberOrdering() {
        when(genealogyQueryService.findMemberSummary(MEMBER))
                .thenReturn(Optional.of(new MemberSummaryDto(MEMBER, "Nguyễn Văn An", CLAN, "BR_1")));
        when(reminderRepository.findAllByMemberId(MEMBER)).thenReturn(List.of(
                DeathAnniversaryReminder.builder().reminderId("GIO_1").memberId(MEMBER)
                        .anniversaryYear(2025).lunarYear(2025).milestoneDays(3)
                        .anniversaryDate(LocalDate.of(2025, 4, 1)).triggerDate(LocalDate.of(2025, 3, 29))
                        .recipientCount(2).build(),
                DeathAnniversaryReminder.builder().reminderId("GIO_2").memberId(MEMBER)
                        .anniversaryYear(2026).lunarYear(2026).milestoneDays(14)
                        .anniversaryDate(LocalDate.of(2026, 4, 3)).triggerDate(LocalDate.of(2026, 3, 20))
                        .recipientCount(1).build()));

        List<DeathAnniversaryReminderDto> result = service.remindersOfMember(MEMBER);

        assertThat(result).extracting(DeathAnniversaryReminderDto::reminderId).containsExactly("GIO_2", "GIO_1");
    }

    @Test
    @DisplayName("a member of another clan answers 404, not an empty list")
    void remindersOfForeignMemberIsNotFound() {
        when(genealogyQueryService.findMemberSummary(MEMBER))
                .thenReturn(Optional.of(new MemberSummaryDto(MEMBER, "Nguyễn Văn An", "CLAN_OTHER", "BR_X")));

        assertThatThrownBy(() -> service.remindersOfMember(MEMBER))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reminderRepository, never()).findAllByMemberId(anyString());
    }

    @Test
    @DisplayName("an unknown member answers 404")
    void remindersOfUnknownMemberIsNotFound() {
        when(genealogyQueryService.findMemberSummary(MEMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remindersOfMember(MEMBER))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
