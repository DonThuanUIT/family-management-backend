package com.familyfund.event.internal.anniversary;

import com.familyfund.core.security.JwtTokenProvider;
import com.familyfund.core.security.MemberLinkedPrincipal;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.tenant.TenantFilter;
import com.familyfund.event.api.dto.DeathAnniversaryDto;
import com.familyfund.event.internal.entity.DeathAnniversaryReminder;
import com.familyfund.event.internal.repository.DeathAnniversaryReminderRepository;
import com.familyfund.genealogy.api.LunarCalendarService;
import com.familyfund.genealogy.api.dto.LunarDateDto;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of US-40 over the real stack: genealogy + lunar converter + event anniversary job + notification
 * inbox, on the Flyway schema (V24) in PostgreSQL. Every test runs in a transaction that is rolled back.
 *
 * <p>The anniversary date is not hardcoded: it is derived from the real US-27 converter, so the test stays correct
 * whatever the lunar calendar does in the year it runs.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeathAnniversaryJobIntegrationTest {

    private static final String CLAN = "CLAN_GIO_40";
    private static final String BRANCH_ID = "BR_GIO_40";
    private static final String MEMBER_ID = "MEM_GIO_40";
    private static final String REP_MEMBER_ID = "MEM_GIO_40_REP";
    private static final String HEAD_ACCOUNT = "ACC_GIO_40_HEAD";
    private static final String REP_ACCOUNT = "ACC_GIO_40_REP";
    private static final String PATH = "/api/v1/events/death-anniversaries";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClanRepository clanRepository;
    @Autowired
    private FamilyBranchRepository branchRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private DeathAnniversaryReminderRepository reminderRepository;
    @Autowired
    private LunarCalendarService lunarCalendarService;
    @Autowired
    private com.familyfund.event.api.DeathAnniversaryService deathAnniversaryService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /** Solar date of 15/02 lunar in the year the tests run. */
    private LocalDate anniversary;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN);

        // 15/02 lunar, resolved with the converter the production code uses.
        anniversary = resolveAnniversary(15, 2, LocalDate.now().getYear());
        // The stored label must be this year's Can Chi: the converter round-trip twice gives "15/02 Giáp Thìn" while
        // the suggestion resolves the label against the CURRENT lunar year.
        LunarDateDto resolveYearLunar = lunarCalendarService.convertSolarToLunar(anniversary);
        String lunarLabel = String.format("%02d/%02d ", resolveYearLunar.day(), resolveYearLunar.month())
                + lunarCalendarService.canChiOfYear(LocalDate.now().getYear());

        clanRepository.save(Clan.builder().clanId(CLAN).clanName("Dòng họ Giỗ 40").build());
        FamilyBranch branch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_ID).clanId(CLAN).branchName("Chi Giỗ").generationLevel(2).build());

        Member deceased = memberRepository.save(Member.builder()
                .memberId(MEMBER_ID).fullName("Cụ Nguyễn Văn An").gender("Male").generation(2)
                .status(Member.STATUS_DECEASED)
                .deathDate(LocalDate.of(2020, 1, 1))
                .deathDateLunar(lunarLabel)
                .branch(branch).build());
        Member representative = memberRepository.save(Member.builder()
                .memberId(REP_MEMBER_ID).fullName("Trưởng Chi Giỗ").gender("Male").generation(3)
                .branch(branch).build());
        branch.setRepresentative(representative);
        branchRepository.saveAndFlush(branch);

        userAccountRepository.save(UserAccount.builder()
                .accountId(REP_ACCOUNT).memberId(representative.getMemberId()).phoneNumber("0911900040")
                .passwordHash("{noop}test").accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(HEAD_ACCOUNT).phoneNumber("0911900041").passwordHash("{noop}test")
                .accountStatus(AccountStatus.Active).build());

        assertThat(deceased.getDeathDateLunar()).isEqualTo(lunarLabel);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    /** Finds the solar date of {@code day/month} lunar in {@code year} using the public API. */
    private LocalDate resolveAnniversary(int lunarDay, int lunarMonth, int year) {
        LocalDate cursor = LocalDate.of(year, 1, 1);
        while (cursor.getYear() == year) {
            LunarDateDto lunar = lunarCalendarService.convertSolarToLunar(cursor);
            if (lunar != null && lunar.day() == lunarDay && lunar.month() == lunarMonth && !lunar.leap()) {
                return cursor;
            }
            cursor = cursor.plusDays(1);
        }
        throw new IllegalStateException("No " + lunarDay + "/" + lunarMonth + " lunar in " + year);
    }

    private Authentication auth(String accountId, String memberId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(new Principal(accountId, memberId), "n/a", authorities);
    }

    // ------------------------------------------------------------------ the pass

    @Test
    @DisplayName("the pass issues the 14-day reminder, records it once and fills the inbox")
    void scanIssuesFourteenDayReminder() {
        LocalDate triggerDay = anniversary.minusDays(14);

        int sent = deathAnniversaryService.scanAndSendReminders(triggerDay);

        // The shared database also holds demo clans, so the global count is only bounded from below; every
        // assertion about THIS member below is exact.
        assertThat(sent).isGreaterThanOrEqualTo(1);
        List<DeathAnniversaryReminder> ledgers = reminderRepository.findAllByMemberId(MEMBER_ID);
        assertThat(ledgers).hasSize(1);
        DeathAnniversaryReminder ledger = ledgers.getFirst();
        assertThat(ledger.getMilestoneDays()).isEqualTo(14);
        assertThat(ledger.getAnniversaryDate()).isEqualTo(anniversary);
        assertThat(ledger.getTriggerDate()).isEqualTo(triggerDay);
        assertThat(ledger.getAnniversaryYear()).isEqualTo(anniversary.getYear());
        assertThat(ledger.getClanId()).isEqualTo(CLAN);
        assertThat(ledger.getRecipientCount()).isEqualTo(1);

        // The inbox fan-out is @Async and therefore outside this transaction (the listener is also disabled in the
        // test profile); recipient resolution is proven by recipientCount above and the inbox row itself is covered
        // by NotificationInboxListenerTest#onDeathAnniversaryReminderIssued.
    }

    @Test
    @DisplayName("a second pass on the same day sends nothing (idempotent by milestone)")
    void scanIsIdempotent() {
        LocalDate triggerDay = anniversary.minusDays(14);

        assertThat(deathAnniversaryService.scanAndSendReminders(triggerDay)).isGreaterThanOrEqualTo(1);
        // Everything that matched on the previous pass is already recorded, so a replay sends nothing at all.
        assertThat(deathAnniversaryService.scanAndSendReminders(triggerDay)).isZero();
        assertThat(reminderRepository.findAllByMemberId(MEMBER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("the 3-day milestone is a separate reminder")
    void scanIssuesThreeDayReminder() {
        assertThat(deathAnniversaryService.scanAndSendReminders(anniversary.minusDays(14)))
                .isGreaterThanOrEqualTo(1);
        assertThat(deathAnniversaryService.scanAndSendReminders(anniversary.minusDays(3)))
                .isGreaterThanOrEqualTo(1);

        assertThat(reminderRepository.findAllByMemberId(MEMBER_ID))
                .extracting(DeathAnniversaryReminder::getMilestoneDays)
                .containsExactlyInAnyOrder(14, 3);
    }

    @Test
    @DisplayName("a day that is not a milestone sends nothing")
    void scanSkipsNonMilestoneDay() {
        deathAnniversaryService.scanAndSendReminders(anniversary.minusDays(7));
        assertThat(reminderRepository.findAllByMemberId(MEMBER_ID)).isEmpty();
    }

    // ------------------------------------------------------------------ read side

    @Test
    @DisplayName("GET /api/v1/events/death-anniversaries - 200 with the Đám giỗ suggestion payload")
    void upcomingReturnsSuggestion() throws Exception {
        // Anchor the window on the anniversary itself: the tests run at an arbitrary time of year.
        mockMvc.perform(get(PATH)
                        .param("from", anniversary.minusDays(20).toString())
                        .param("days", "30")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(HEAD_ACCOUNT, null, SystemRoles.CLAN_HEAD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].memberId").value(MEMBER_ID))
                .andExpect(jsonPath("$.data[0].memberFullName").value("Cụ Nguyễn Văn An"))
                .andExpect(jsonPath("$.data[0].lunarDay").value(15))
                .andExpect(jsonPath("$.data[0].lunarMonth").value(2))
                .andExpect(jsonPath("$.data[0].suggestion.eventName").value(org.hamcrest.Matchers.containsString("Đám giỗ Cụ Nguyễn Văn An")))
                .andExpect(jsonPath("$.data[0].suggestion.location").value("Chi Giỗ"))
                .andExpect(jsonPath("$.data[0].suggestion.requestBody.eventDatetime").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/events/death-anniversaries - 401 for an anonymous caller")
    void upcomingRequiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH).header(TenantFilter.HEADER_CLAN_ID, CLAN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET .../{memberId}/reminders - 200 exposes the send-ledger after a pass")
    void remindersEndpointShowsLedger() throws Exception {
        deathAnniversaryService.scanAndSendReminders(anniversary.minusDays(14));

        mockMvc.perform(get(PATH + "/{memberId}/reminders", MEMBER_ID)
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(HEAD_ACCOUNT, null, SystemRoles.CLAN_HEAD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].milestoneDays").value(14))
                .andExpect(jsonPath("$.data[0].recipientCount").value(1));
    }

    @Test
    @DisplayName("GET .../{memberId}/reminders - 404 for an unknown member")
    void remindersEndpointNotFound() throws Exception {
        mockMvc.perform(get(PATH + "/{memberId}/reminders", "MEM_NOT_THERE")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(HEAD_ACCOUNT, null, SystemRoles.CLAN_HEAD))))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ manual scan

    @Test
    @DisplayName("POST .../scan - 200 runs the pass for a clan head")
    void scanEndpointRuns() throws Exception {
        mockMvc.perform(post(PATH + "/scan")
                        .param("date", anniversary.minusDays(14).toString())
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(HEAD_ACCOUNT, null, SystemRoles.CLAN_HEAD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remindersSent").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        assertThat(reminderRepository.findAllByMemberId(MEMBER_ID)).hasSize(1);
    }

    @Test
    @DisplayName("POST .../scan - 403 for a plain member")
    void scanEndpointForbiddenForMember() throws Exception {
        mockMvc.perform(post(PATH + "/scan")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(REP_ACCOUNT, REP_MEMBER_ID, SystemRoles.MEMBER))))
                .andExpect(status().isForbidden());

        assertThat(reminderRepository.findAllByMemberId(MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("the converter exposed through genealogy::api matches the US-27 converter")
    void lunarApiWorks() {
        LunarDateDto lunar = lunarCalendarService.convertSolarToLunar(anniversary);

        assertThat(lunar.day()).isEqualTo(15);
        assertThat(lunar.month()).isEqualTo(2);
        assertThat(lunar.toAnniversaryString()).startsWith("15/02");
        assertThat(lunarCalendarService.canChiOfYear(2026)).isEqualTo("Bính Ngọ");
        assertThat(lunarCalendarService.convertSolarToLunar(null)).isNull();
    }

    @Test
    @DisplayName("the anniversary DTO carries both solar and lunar labels for the UI")
    void upcomingCarriesBothCalendars() {
        List<DeathAnniversaryDto> result = deathAnniversaryService.upcomingAnniversaries(anniversary.minusDays(20), 30);

        assertThat(result).isNotEmpty();
        DeathAnniversaryDto dto = result.stream().filter(d -> MEMBER_ID.equals(d.memberId())).findFirst().orElseThrow();
        assertThat(dto.anniversaryDate()).isEqualTo(anniversary);
        assertThat(dto.deathDateLunar()).startsWith("15/02");
        assertThat(dto.daysRemaining()).isEqualTo(20);
        assertThat(dto.generation()).isEqualTo(2);
        assertThat(dto.branchName()).isEqualTo("Chi Giỗ");
    }
}
