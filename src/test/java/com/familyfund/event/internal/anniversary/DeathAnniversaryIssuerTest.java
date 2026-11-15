package com.familyfund.event.internal.anniversary;

import com.familyfund.event.api.dto.DeathAnniversaryDto;
import com.familyfund.event.api.event.DeathAnniversaryReminderIssuedDomainEvent;
import com.familyfund.event.internal.entity.DeathAnniversaryReminder;
import com.familyfund.event.internal.repository.DeathAnniversaryReminderRepository;
import com.familyfund.event.internal.util.AnniversaryKeyGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.LunarCalendarService;
import com.familyfund.genealogy.api.dto.BranchSummaryDto;
import com.familyfund.genealogy.api.dto.DeceasedMemberDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link DeathAnniversaryIssuer} (US-40): milestone gating, the send-ledger row, recipient resolution
 * and the published domain event.
 */
@ExtendWith(MockitoExtension.class)
class DeathAnniversaryIssuerTest {

    private static final String CLAN = "CLAN_GIO_IT";
    private static final String MEMBER = "MEM_GIO_IT";
    private static final String BRANCH = "BR_GIO_IT";
    private static final String REP_MEMBER = "MEM_GIO_REP";
    private static final String REMINDER_ID = "GIO_TEST_0001";
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 20);
    private static final LocalDate ANNIVERSARY = TODAY.plusDays(14);

    @Mock
    private DeathAnniversaryCalculator calculator;
    @Mock
    private DeathAnniversaryReminderRepository reminderRepository;
    @Mock
    private AnniversaryKeyGenerator keyGenerator;
    @Mock
    private GenealogyQueryService genealogyQueryService;
    @Mock
    private IdentityRoleCommandService identityRoleCommandService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private LunarCalendarService lunarCalendarService;

    @InjectMocks
    private DeathAnniversaryIssuer issuer;

    private DeathAnniversaryProperties properties() {
        return new DeathAnniversaryProperties(true, null, "Asia/Ho_Chi_Minh", List.of(14, 3), 30, null, null, null);
    }

    private DeceasedMemberDto member() {
        return DeceasedMemberDto.builder()
                .memberId(MEMBER).clanId(CLAN).fullName("Nguyễn Văn An").generation(3)
                .branchId(BRANCH).branchName("Chi Một")
                .deathDate(LocalDate.of(2020, 3, 8)).deathDateLunar("15/02 Giáp Thìn")
                .build();
    }

    private DeathAnniversaryDto anniversary(Integer milestone) {
        return DeathAnniversaryDto.builder()
                .memberId(MEMBER).memberFullName("Nguyễn Văn An").generation(3)
                .branchId(BRANCH).branchName("Chi Một")
                .deathDateLunar("15/02 Giáp Thìn").lunarDay(15).lunarMonth(2).leapMonth(false)
                .anniversaryYear(2026).anniversaryDate(ANNIVERSARY).daysRemaining(14)
                .milestoneDays(milestone).reminderTriggered(false).alreadyReminded(false)
                .build();
    }

    private AccountSummaryDto account(String accountId, String memberId) {
        return new AccountSummaryDto(accountId, memberId, "0900000000", null, "Active", Set.of());
    }

    private void wireIssuer(DeathAnniversaryProperties props) {
        // Constructed explicitly: @InjectMocks cannot inject the properties record reliably.
        issuer = new DeathAnniversaryIssuer(calculator, reminderRepository, keyGenerator,
                genealogyQueryService, lunarCalendarService, identityRoleCommandService, eventPublisher, props);
    }

    @Test
    @DisplayName("a milestone day sends the reminder, records the ledger row and publishes the event")
    void issuesReminderOnMilestone() {
        wireIssuer(properties());
        when(calculator.compute(any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(Optional.of(anniversary(14)));
        when(reminderRepository.existsByMemberIdAndLunarYearAndMilestoneDays(MEMBER, 2026, 14)).thenReturn(false);
        when(genealogyQueryService.getMemberIdsByBranch(CLAN, BRANCH)).thenReturn(List.of(MEMBER));
        when(identityRoleCommandService.findAccountsByMemberIds(List.of(MEMBER)))
                .thenReturn(Map.of(MEMBER, account("ACC_MEMBER", MEMBER)));
        when(genealogyQueryService.findBranchByIdAndClanId(BRANCH, CLAN)).thenReturn(Optional.of(
                new BranchSummaryDto(BRANCH, CLAN, "Chi Một", 2, REP_MEMBER, "Nguyễn Văn Trưởng")));
        when(identityRoleCommandService.findAccountByMemberId(REP_MEMBER))
                .thenReturn(Optional.of(account("ACC_REP", REP_MEMBER)));
        when(keyGenerator.generateReminderId()).thenReturn(REMINDER_ID);

        boolean issued = issuer.issueReminder(member(), TODAY);

        assertThat(issued).isTrue();
        ArgumentCaptor<DeathAnniversaryReminder> ledger = ArgumentCaptor.forClass(DeathAnniversaryReminder.class);
        verify(reminderRepository).save(ledger.capture());
        assertThat(ledger.getValue().getReminderId()).isEqualTo(REMINDER_ID);
        assertThat(ledger.getValue().getClanId()).isEqualTo(CLAN);
        assertThat(ledger.getValue().getMemberId()).isEqualTo(MEMBER);
        assertThat(ledger.getValue().getAnniversaryYear()).isEqualTo(2026);
        assertThat(ledger.getValue().getLunarYear()).isEqualTo(2026);
        assertThat(ledger.getValue().getMilestoneDays()).isEqualTo(14);
        assertThat(ledger.getValue().getAnniversaryDate()).isEqualTo(ANNIVERSARY);
        assertThat(ledger.getValue().getTriggerDate()).isEqualTo(TODAY);
        assertThat(ledger.getValue().getRecipientCount()).isEqualTo(2);

        ArgumentCaptor<DeathAnniversaryReminderIssuedDomainEvent> published =
                ArgumentCaptor.forClass(DeathAnniversaryReminderIssuedDomainEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        DeathAnniversaryReminderIssuedDomainEvent event = published.getValue();
        assertThat(event.clanId()).isEqualTo(CLAN);
        assertThat(event.memberId()).isEqualTo(MEMBER);
        assertThat(event.milestoneDays()).isEqualTo(14);
        assertThat(event.daysRemaining()).isEqualTo(14);
        assertThat(event.recipientAccountIds()).containsExactly("ACC_MEMBER", "ACC_REP");
        assertThat(event.title()).isEqualTo("Nhắc giỗ Nguyễn Văn An — còn 14 ngày");
        assertThat(event.content())
                .contains("Nguyễn Văn An")
                .contains("đời thứ 3")
                .contains("15/02 Giáp Thìn")
                .contains(ANNIVERSARY.toString())
                .contains("còn 14 ngày")
                .contains("Chi Một");
    }

    @Test
    @DisplayName("a day that is not a milestone sends nothing")
    void skipsNonMilestoneDay() {
        wireIssuer(properties());
        when(calculator.compute(any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(Optional.of(anniversary(null)));

        assertThat(issuer.issueReminder(member(), TODAY)).isFalse();
        verify(reminderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("a milestone already sent for this year is skipped (idempotency)")
    void skipsAlreadySent() {
        wireIssuer(properties());
        when(calculator.compute(any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(Optional.of(anniversary(3)));
        when(reminderRepository.existsByMemberIdAndLunarYearAndMilestoneDays(MEMBER, 2026, 3)).thenReturn(true);

        assertThat(issuer.issueReminder(member(), TODAY)).isFalse();
        verify(reminderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("an unresolvable anniversary is skipped")
    void skipsUnresolvable() {
        wireIssuer(properties());
        when(calculator.compute(any(), any(), any(), any(), any(Boolean.class))).thenReturn(Optional.empty());

        assertThat(issuer.issueReminder(member(), TODAY)).isFalse();
        verify(reminderRepository, never()).save(any());
    }

    @Test
    @DisplayName("a member without clan is skipped before touching the calculator")
    void skipsMalformedMember() {
        wireIssuer(properties());
        DeceasedMemberDto noClan = DeceasedMemberDto.builder().memberId(MEMBER).clanId(null).build();

        assertThat(issuer.issueReminder(noClan, TODAY)).isFalse();
        assertThat(issuer.issueReminder(null, TODAY)).isFalse();
        verifyNoInteractions(calculator);
    }

    @Test
    @DisplayName("a branch without accounts still records the reminder, with zero recipients")
    void recordsWithoutRecipients() {
        wireIssuer(properties());
        when(calculator.compute(any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(Optional.of(anniversary(14)));
        when(genealogyQueryService.getMemberIdsByBranch(CLAN, BRANCH)).thenReturn(List.of());
        when(genealogyQueryService.findBranchByIdAndClanId(BRANCH, CLAN)).thenReturn(Optional.empty());
        when(keyGenerator.generateReminderId()).thenReturn(REMINDER_ID);

        assertThat(issuer.issueReminder(member(), TODAY)).isTrue();

        ArgumentCaptor<DeathAnniversaryReminder> ledger = ArgumentCaptor.forClass(DeathAnniversaryReminder.class);
        verify(reminderRepository).save(ledger.capture());
        assertThat(ledger.getValue().getRecipientCount()).isZero();

        ArgumentCaptor<DeathAnniversaryReminderIssuedDomainEvent> published =
                ArgumentCaptor.forClass(DeathAnniversaryReminderIssuedDomainEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().recipientAccountIds()).isEmpty();
        assertThat(published.getValue().content()).contains("Chưa có tài khoản nào");
    }

    @Test
    @DisplayName("the representative is not duplicated when they are also a branch member")
    void deduplicatesRecipients() {
        wireIssuer(properties());
        when(calculator.compute(any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(Optional.of(anniversary(14)));
        when(genealogyQueryService.getMemberIdsByBranch(CLAN, BRANCH)).thenReturn(List.of(REP_MEMBER));
        when(identityRoleCommandService.findAccountsByMemberIds(List.of(REP_MEMBER)))
                .thenReturn(Map.of(REP_MEMBER, account("ACC_REP", REP_MEMBER)));
        when(genealogyQueryService.findBranchByIdAndClanId(BRANCH, CLAN)).thenReturn(Optional.of(
                new BranchSummaryDto(BRANCH, CLAN, "Chi Một", 2, REP_MEMBER, "Nguyễn Văn Trưởng")));
        when(identityRoleCommandService.findAccountByMemberId(REP_MEMBER))
                .thenReturn(Optional.of(account("ACC_REP", REP_MEMBER)));
        when(keyGenerator.generateReminderId()).thenReturn(REMINDER_ID);

        issuer.issueReminder(member(), TODAY);

        ArgumentCaptor<DeathAnniversaryReminderIssuedDomainEvent> published =
                ArgumentCaptor.forClass(DeathAnniversaryReminderIssuedDomainEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().recipientAccountIds()).containsExactly("ACC_REP");
    }

    @Test
    @DisplayName("a member without a branch only notifies nobody but still records")
    void memberWithoutBranch() {
        wireIssuer(properties());
        when(calculator.compute(any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(Optional.of(anniversary(3)));
        when(keyGenerator.generateReminderId()).thenReturn(REMINDER_ID);
        DeceasedMemberDto noBranch = DeceasedMemberDto.builder()
                .memberId(MEMBER).clanId(CLAN).fullName("Nguyễn Văn An")
                .branchId(null).deathDateLunar("15/02 Giáp Thìn").build();

        assertThat(issuer.issueReminder(noBranch, TODAY)).isTrue();

        verify(genealogyQueryService, never()).getMemberIdsByBranch(anyString(), anyString());
        verify(reminderRepository).save(any(DeathAnniversaryReminder.class));
    }

    @Test
    @DisplayName("the calculator receives the configured zone, not the JVM default")
    void passesConfiguredZone() {
        wireIssuer(properties());
        when(calculator.compute(any(), any(), any(), any(), any(Boolean.class))).thenReturn(Optional.empty());

        issuer.issueReminder(member(), TODAY);

        ArgumentCaptor<java.time.ZoneId> zone = ArgumentCaptor.forClass(java.time.ZoneId.class);
        verify(calculator).compute(any(), any(), zone.capture(), any(), any(Boolean.class));
        assertThat(zone.getValue()).isEqualTo(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        verify(lunarCalendarService, never()).convertSolarToLunar(any());
        verify(reminderRepository, never()).existsByMemberIdAndLunarYearAndMilestoneDays(anyString(), anyInt(), anyInt());
    }
}
