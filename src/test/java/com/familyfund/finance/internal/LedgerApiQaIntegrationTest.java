package com.familyfund.finance.internal;

import com.familyfund.core.exception.LedgerImmutabilityViolationException;
import com.familyfund.core.security.MemberLinkedPrincipal;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.tenant.TenantFilter;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.entity.CommonFundDisbursementRequest;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.repository.CommonFundDisbursementRequestRepository;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.util.ImmutableLedgerHashCalculator;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import com.familyfund.genealogy.internal.repository.MemberRepository;
import com.familyfund.identity.internal.entity.AccountRole;
import com.familyfund.identity.internal.entity.AccountRoleId;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.AccountRoleRepository;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive QA integration suite exercising all Ledger APIs across happy paths,
 * exact boundaries, type/format/malformed payloads, non-existent/cross-tenant references,
 * state machine violations, auth/contextual RBAC, and US-61 immutability/CORS guards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LedgerApiQaIntegrationTest {

    private static final String CLAN_ID = "CLN_QA_01";
    private static final String CLAN_EMPTY = "CLN_QA_EMPTY";
    private static final String CLAN_OTHER = "CLN_QA_OTHER";

    private static final String BRANCH_ID = "BR_QA_01";
    private static final String BRANCH_OTHER = "BR_QA_OTHER";

    private static final String EVT_A = "EVT_QA_A";
    private static final String EVT_B = "EVT_QA_B";

    private static final String TX_BASE = "LDG_QA_BASE";
    private static final String TX_EVT_B = "LDG_QA_EVT_B";
    private static final String TX_DISB = "LDG_QA_DISB";
    private static final String TX_OTHER = "LDG_QA_OTHER";
    private static final String CFD_SEED = "CFD_QA_SEED";

    private static final String RAW_PASSWORD = "QaHead@2026!";

    private static final String MEM_HEAD = "MEM_QA_HEAD";
    private static final String ACC_HEAD = "ACC_QA_HEAD";

    private static final String MEM_LOCK = "MEM_QA_LOCK";
    private static final String ACC_LOCK = "ACC_QA_LOCK";

    private static final String MEM_DEACT = "MEM_QA_DEACT";
    private static final String ACC_DEACT = "ACC_QA_DEACT";

    private static final String MEM_PEND = "MEM_QA_PEND";
    private static final String ACC_PEND = "ACC_QA_PEND";

    private static final String MEM_BR = "MEM_QA_BR";
    private static final String ACC_BR = "ACC_QA_BR";

    private static final String MEM_TR = "MEM_QA_TR";
    private static final String ACC_TR = "ACC_QA_TR";

    private static final String MEM_HO = "MEM_QA_HO";
    private static final String ACC_HO = "ACC_QA_HO";

    private static final String MEM_ORD = "MEM_QA_ORD";
    private static final String ACC_ORD = "ACC_QA_ORD";

    private static final String MEM_OTHER = "MEM_QA_OTHER";
    private static final String ACC_OTHER = "ACC_QA_OTHER";

    private static final String MEM_ADMIN = "MEM_QA_ADM";
    private static final String ACC_ADMIN = "ACC_QA_ADMIN";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ClanRepository clanRepository;

    @Autowired
    private FamilyBranchRepository branchRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AccountRoleRepository accountRoleRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventPersonnelRepository eventPersonnelRepository;

    @Autowired
    private CommonFundDisbursementRequestRepository disbursementRepository;

    @Autowired
    private ImmutableLedgerRepository immutableLedgerRepository;

    @Autowired
    private ImmutableLedgerHashCalculator ledgerHashCalculator;

    @Autowired
    private EntityManager entityManager;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seedQaData() {
        TenantContext.setClanId(CLAN_ID);

        clanRepository.save(Clan.builder().clanId(CLAN_ID).clanName("Dòng họ QA Test").build());
        clanRepository.save(Clan.builder().clanId(CLAN_EMPTY).clanName("Dòng họ QA Trống").build());
        clanRepository.save(Clan.builder().clanId(CLAN_OTHER).clanName("Dòng họ QA Khác").build());

        FamilyBranch branch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_ID).clanId(CLAN_ID).branchName("Chi Gốc QA").generationLevel(1).build());
        FamilyBranch otherBranch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_OTHER).clanId(CLAN_OTHER).branchName("Chi Gốc Khác").generationLevel(1).build());

        Member mHead = memberRepository.save(Member.builder()
                .memberId(MEM_HEAD).branch(branch).fullName("Trưởng Tộc QA").gender("Male").generation(1).status("Living").build());
        Member mLock = memberRepository.save(Member.builder()
                .memberId(MEM_LOCK).branch(branch).fullName("Trưởng Tộc Bị Khóa").gender("Male").generation(1).status("Living").build());
        Member mDeact = memberRepository.save(Member.builder()
                .memberId(MEM_DEACT).branch(branch).fullName("Trưởng Tộc Vô Hiệu").gender("Male").generation(1).status("Living").build());
        Member mPend = memberRepository.save(Member.builder()
                .memberId(MEM_PEND).branch(branch).fullName("Trưởng Tộc Chờ Duyệt").gender("Male").generation(1).status("Living").build());
        Member mBranch = memberRepository.save(Member.builder()
                .memberId(MEM_BR).branch(branch).fullName("Trưởng Chi QA").gender("Male").generation(2).status("Living").build());
        Member mTreasurer = memberRepository.save(Member.builder()
                .memberId(MEM_TR).branch(branch).fullName("Thủ Quỹ Sự Kiện A").gender("Female").generation(2).status("Living").build());
        Member mHo = memberRepository.save(Member.builder()
                .memberId(MEM_HO).branch(branch).fullName("Trưởng BTC Sự Kiện A").gender("Male").generation(2).status("Living").build());
        Member mOrdinary = memberRepository.save(Member.builder()
                .memberId(MEM_ORD).branch(branch).fullName("Thành Viên QA").gender("Male").generation(3).status("Living").build());
        Member mAdmin = memberRepository.save(Member.builder()
                .memberId(MEM_ADMIN).branch(branch).fullName("Quản Trị Viên QA").gender("Male").generation(1).status("Living").build());
        Member mOther = memberRepository.save(Member.builder()
                .memberId(MEM_OTHER).branch(otherBranch).fullName("Người Dòng Họ Khác").gender("Female").generation(2).status("Living").build());

        String encodedPw = passwordEncoder.encode(RAW_PASSWORD);
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_HEAD).memberId(mHead.getMemberId()).phoneNumber("0988000001")
                .passwordHash(encodedPw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_LOCK).memberId(mLock.getMemberId()).phoneNumber("0988000002")
                .passwordHash(encodedPw).accountStatus(AccountStatus.Suspended).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_DEACT).memberId(mDeact.getMemberId()).phoneNumber("0988000006")
                .passwordHash(encodedPw).accountStatus(AccountStatus.Deactivated).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_PEND).memberId(mPend.getMemberId()).phoneNumber("0988000007")
                .passwordHash(encodedPw).accountStatus(AccountStatus.PendingApproval).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_BR).memberId(mBranch.getMemberId()).phoneNumber("0988000003")
                .passwordHash(encodedPw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_TR).memberId(mTreasurer.getMemberId()).phoneNumber("0988000004")
                .passwordHash(encodedPw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_HO).memberId(mHo.getMemberId()).phoneNumber("0988000008")
                .passwordHash(encodedPw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_ORD).memberId(mOrdinary.getMemberId()).phoneNumber("0988000005")
                .passwordHash(encodedPw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_ADMIN).memberId(mAdmin.getMemberId()).phoneNumber("0988000009")
                .passwordHash(encodedPw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_OTHER).memberId(mOther.getMemberId()).phoneNumber("0988000010")
                .passwordHash(encodedPw).accountStatus(AccountStatus.Active).build());

        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_HEAD, SystemRoles.CLAN_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_LOCK, SystemRoles.CLAN_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_DEACT, SystemRoles.CLAN_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_PEND, SystemRoles.CLAN_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_BR, SystemRoles.BRANCH_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_ADMIN, SystemRoles.ADMIN)).build());

        Instant evtTime = Instant.now().minus(5, ChronoUnit.DAYS);
        eventRepository.save(Event.builder()
                .eventId(EVT_A).clanId(CLAN_ID).eventName("Sự kiện QA A")
                .eventDatetime(evtTime).location("Từ đường A").createdBy(ACC_HEAD).status(EventStatus.Ended).build());
        eventRepository.save(Event.builder()
                .eventId(EVT_B).clanId(CLAN_ID).eventName("Sự kiện QA B")
                .eventDatetime(evtTime).location("Từ đường B").createdBy(ACC_HEAD).status(EventStatus.Ended).build());

        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVT_A).memberId(MEM_TR).eventRole(EventRole.EventTreasurer).isReadOnly(false).build());
        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVT_A).memberId(MEM_HO).eventRole(EventRole.HeadOfOrganizer).isReadOnly(false).build());

        Instant t1 = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        String h1 = ledgerHashCalculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH, TX_BASE, CLAN_ID, EVT_A, null, null, null,
                LedgerTransactionType.SurplusTransfer, new BigDecimal("100000000.00"), new BigDecimal("100000000.00"),
                "Kết dư sự kiện A", ACC_TR, ACC_HEAD, t1
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(TX_BASE).clanId(CLAN_ID).eventId(EVT_A)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00")).runningBalance(new BigDecimal("100000000.00"))
                .description("Kết dư sự kiện A").createdByAccountId(ACC_TR).approvedByAccountId(ACC_HEAD)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH).recordHash(h1).createdAt(t1)
                .build());

        Instant t2 = t1.plus(10, ChronoUnit.MINUTES);
        String h2 = ledgerHashCalculator.computeRecordHash(
                h1, TX_EVT_B, CLAN_ID, EVT_B, null, null, null,
                LedgerTransactionType.SurplusTransfer, new BigDecimal("20000000.00"), new BigDecimal("120000000.00"),
                "Kết dư sự kiện B", ACC_HEAD, ACC_HEAD, t2
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(TX_EVT_B).clanId(CLAN_ID).eventId(EVT_B)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("20000000.00")).runningBalance(new BigDecimal("120000000.00"))
                .description("Kết dư sự kiện B").createdByAccountId(ACC_HEAD).approvedByAccountId(ACC_HEAD)
                .previousHash(h1).recordHash(h2).createdAt(t2)
                .build());

        disbursementRepository.saveAndFlush(CommonFundDisbursementRequest.builder()
                .requestId(CFD_SEED).clanId(CLAN_ID).title("Chi nghi lễ đã duyệt sẵn")
                .category(CommonFundExpenseCategory.Ceremony).amount(new BigDecimal("20000000.00"))
                .purpose("Chi lễ đầu năm").proposedByAccountId(ACC_HEAD)
                .approvalStatus(CommonFundDisbursementStatus.Approved).reviewedByAccountId(ACC_HEAD)
                .createdAt(t2.plusSeconds(60)).reviewedAt(t2.plusSeconds(120))
                .build());

        Instant t3 = t2.plus(10, ChronoUnit.MINUTES);
        String h3 = ledgerHashCalculator.computeRecordHash(
                h2, TX_DISB, CLAN_ID, null, null, CFD_SEED, null,
                LedgerTransactionType.CommonFundDisbursement, new BigDecimal("-20000000.00"), new BigDecimal("100000000.00"),
                "Chi nghi lễ đã duyệt sẵn", ACC_HEAD, ACC_HEAD, t3
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(TX_DISB).clanId(CLAN_ID).disbursementRequestId(CFD_SEED)
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(new BigDecimal("-20000000.00")).runningBalance(new BigDecimal("100000000.00"))
                .description("Chi nghi lễ đã duyệt sẵn").createdByAccountId(ACC_HEAD).approvedByAccountId(ACC_HEAD)
                .previousHash(h2).recordHash(h3).createdAt(t3)
                .build());

        // Foreign clan ledger entry for cross-tenant filter verification
        String hOther = ledgerHashCalculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH, TX_OTHER, CLAN_OTHER, null, null, null, null,
                LedgerTransactionType.SurplusTransfer, new BigDecimal("50000000.00"), new BigDecimal("50000000.00"),
                "Sổ cái dòng họ khác", ACC_OTHER, ACC_OTHER, t1
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(TX_OTHER).clanId(CLAN_OTHER)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("50000000.00")).runningBalance(new BigDecimal("50000000.00"))
                .description("Sổ cái dòng họ khác").createdByAccountId(ACC_OTHER).approvedByAccountId(ACC_OTHER)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH).recordHash(hOther).createdAt(t1)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Authentication auth(String accountId, String memberId, String... roles) {
        List<SimpleGrantedAuthority> auths = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new UsernamePasswordAuthenticationToken(new Principal(accountId, memberId), "N/A", auths);
    }

    private static String json(Map<String, Object> map) {
        return JSON.writeValueAsString(map);
    }

    @Test
    @DisplayName("QA Group 1: Empty clan defaults, exact zero-balance boundaries & Unicode/SQLi payload integrity")
    void qaGroup1_happyPathAndExactBoundaries() throws Exception {
        // 1A. Brand-new empty clan
        mockMvc.perform(get("/api/v1/ledger/balance")
                        .with(authentication(auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_EMPTY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalance").value(0.00))
                .andExpect(jsonPath("$.data.pendingDisbursementAmount").value(0.00))
                .andExpect(jsonPath("$.data.latestTransactionId").doesNotExist());

        mockMvc.perform(get("/api/v1/ledger/verify-integrity")
                        .with(authentication(auth(ACC_ORD, MEM_ORD, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_EMPTY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalEntries").value(0))
                .andExpect(jsonPath("$.data.brokenLinks").isEmpty());

        mockMvc.perform(get("/api/v1/ledger/adjustments")
                        .with(authentication(auth(ACC_ORD, MEM_ORD, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_EMPTY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/v1/ledger/disbursements")
                        .with(authentication(auth(ACC_ORD, MEM_ORD, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_EMPTY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // Propose disbursement on 0 balance -> 400
        mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .with(authentication(auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_EMPTY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Chi thử trên quỹ trống",
                                "category", "Other",
                                "amount", 0.01,
                                "purpose", "Kiểm tra số dư bằng 0"
                        ))))
                .andExpect(status().isBadRequest());

        // 1B. Exceeding balance by exact 0.01 -> 400
        mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .with(authentication(auth(ACC_TR, MEM_TR, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Chi vượt 1 xu",
                                "category", "Other",
                                "amount", new BigDecimal("100000000.01"),
                                "purpose", "Kiểm tra biên vượt 0.01"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("không đủ")));

        // 1C. Unicode + SQLi/XSS-like characters + exact 100% balance boundary (100,000,000.00 -> 0.00)
        String specialPurpose = "Chi sửa mái '; DROP TABLE immutable_ledgers; -- <script>alert(1)</script> (họ Nguyễn)";
        String exactProposeRes = mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .with(authentication(auth(ACC_TR, MEM_TR, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Tu sửa toàn bộ Từ đường",
                                "category", "TempleMaintenance",
                                "amount", new BigDecimal("100000000.00"),
                                "purpose", specialPurpose
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.purpose").value(specialPurpose))
                .andReturn().getResponse().getContentAsString();
        String exactDisbId = com.jayway.jsonpath.JsonPath.read(exactProposeRes, "$.data.requestId");

        // Also test negative adjustment exceeding balance by -0.01 at review time
        String adjOverRes = mockMvc.perform(post("/api/v1/ledger/propose-adjustment")
                        .with(authentication(auth(ACC_TR, MEM_TR, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", TX_BASE,
                                "proposedAdjustmentAmount", new BigDecimal("-100000000.01"),
                                "adjustmentReason", "Kiểm tra biên âm vượt 0.01"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String adjOverId = com.jayway.jsonpath.JsonPath.read(adjOverRes, "$.data.requestId");

        mockMvc.perform(post("/api/v1/ledger/review-adjustment")
                        .with(authentication(auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", adjOverId, "approved", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("bị âm")));

        // Approve exact 100% balance disbursement -> running_balance == 0.00
        mockMvc.perform(post("/api/v1/ledger/disbursements/" + exactDisbId + "/review")
                        .with(authentication(auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "approved", true,
                                "confirmPassword", RAW_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(0.00))
                .andExpect(jsonPath("$.data.ledgerEntry.recordHash", Matchers.matchesPattern("^[0-9a-f]{64}$")));

        mockMvc.perform(get("/api/v1/ledger/balance")
                        .with(authentication(auth(ACC_ORD, MEM_ORD, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalance").value(0.00));

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/ledger/verify-integrity")
                        .with(authentication(auth(ACC_ORD, MEM_ORD, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalEntries").value(4))
                .andExpect(jsonPath("$.data.verifiedEntries").value(4));
    }

    @Test
    @DisplayName("QA Group 2: Empty bodies, malformed JSON, wrong JSON data types, invalid query enums & wrong HTTP verbs")
    void qaGroup2_malformedPayloadsWrongTypesAndInvalidEnums() throws Exception {
        Authentication headAuth = auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD");

        List<String> postRoutes = List.of(
                "/api/v1/ledger/propose-adjustment",
                "/api/v1/ledger/review-adjustment",
                "/api/v1/ledger/disbursements",
                "/api/v1/ledger/review-disbursement"
        );

        for (String route : postRoutes) {
            // Empty body
            mockMvc.perform(post(route).with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            // Malformed JSON
            mockMvc.perform(post(route).with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"broken\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            // Empty JSON {}
            mockMvc.perform(post(route).with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            // Wrong Content-Type text/plain
            mockMvc.perform(post(route).with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("plain text"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.success").value(false));
        }

        // Wrong field types in JSON body -> 400 Bad Request
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalTransactionId":"LDG_QA_BASE","proposedAdjustmentAmount":"not-a-number","adjustmentReason":"Test"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalTransactionId":"LDG_QA_BASE","proposedAdjustmentAmount":[100,200],"adjustmentReason":"Test"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ledger/review-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"LAR_01","approved":"not-a-boolean"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Sửa mái","category":"INVALID_ENUM","amount":500000,"purpose":"Test"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Sửa mái","category":"TempleMaintenance","amount":"five_million","purpose":"Test"}
                                """))
                .andExpect(status().isBadRequest());

        // Invalid Query Param Enums -> MUST return 400 Bad Request (not 500!)
        mockMvc.perform(get("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("category", "NON_EXISTENT_CATEGORY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/v1/ledger/adjustments").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("status", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Wrong HTTP verb on existing GET-only / POST-only routes -> 405
        mockMvc.perform(post("/api/v1/ledger/balance").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/ledger/verify-integrity").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/v1/ledger/review-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/v1/ledger/review-disbursement").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("QA Group 3: Numeric precision/overflow boundaries & blank/oversized strings (@Size/@Digits/@NotBlank)")
    void qaGroup3_numericPrecisionAndStringBoundaries() throws Exception {
        Authentication headAuth = auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD");

        // Numeric boundaries on POST /disbursements
        for (String badAmount : List.of("0", "-1000.00", "0.001", "10000000000000000.00")) {
            mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"title":"Kiểm tra số","category":"Other","amount":%s,"purpose":"Test"}
                                    """.formatted(badAmount)))
                    .andExpect(status().isBadRequest());
        }

        // Numeric boundaries on POST /propose-adjustment
        for (String badAdj : List.of("0", "0.00", "0.001", "-0.001", "10000000000000000.00")) {
            mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"originalTransactionId":"LDG_QA_BASE","proposedAdjustmentAmount":%s,"adjustmentReason":"Test"}
                                    """.formatted(badAdj)))
                    .andExpect(status().isBadRequest());
        }

        // Whitespace-only required strings
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", "   ",
                                "proposedAdjustmentAmount", -1000,
                                "adjustmentReason", "   "
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.originalTransactionId").exists())
                .andExpect(jsonPath("$.errors.adjustmentReason").exists());

        mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "   ",
                                "category", "Other",
                                "amount", 1000,
                                "purpose", "   "
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.purpose").exists());

        // Oversized fields (> @Size max)
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", "X".repeat(21),
                                "proposedAdjustmentAmount", 1000,
                                "adjustmentReason", "A".repeat(2001),
                                "evidenceAttachmentUrl", "https://example.com/" + "u".repeat(500)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.originalTransactionId").exists())
                .andExpect(jsonPath("$.errors.adjustmentReason").exists())
                .andExpect(jsonPath("$.errors.evidenceAttachmentUrl").exists());

        Map<String, Object> oversizedDisb = new LinkedHashMap<>();
        oversizedDisb.put("title", "T".repeat(256));
        oversizedDisb.put("category", "Other");
        oversizedDisb.put("amount", 1000);
        oversizedDisb.put("beneficiaryName", "B".repeat(256));
        oversizedDisb.put("beneficiaryMemberId", "M".repeat(21));
        oversizedDisb.put("purpose", "P".repeat(2001));
        oversizedDisb.put("attachmentUrl", "A".repeat(501));

        mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(oversizedDisb)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.beneficiaryName").exists())
                .andExpect(jsonPath("$.errors.beneficiaryMemberId").exists())
                .andExpect(jsonPath("$.errors.purpose").exists())
                .andExpect(jsonPath("$.errors.attachmentUrl").exists());

        mockMvc.perform(post("/api/v1/ledger/review-disbursement").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "requestId", "R".repeat(21),
                                "approved", true,
                                "confirmPassword", "P".repeat(201),
                                "rejectionReason", "X".repeat(2001)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.requestId").exists())
                .andExpect(jsonPath("$.errors.confirmPassword").exists())
                .andExpect(jsonPath("$.errors.rejectionReason").exists());
    }

    @Test
    @DisplayName("QA Group 4: Non-existent IDs & cross-tenant IDOR isolation (404)")
    void qaGroup4_nonExistentReferencesAndCrossTenantIsolation() throws Exception {
        Authentication headAuth = auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD");

        // Create 1 adjustment and 1 disbursement under CLAN_ID for cross-clan IDOR verification
        String adjRes = mockMvc.perform(post("/api/v1/ledger/propose-adjustment")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", TX_BASE,
                                "proposedAdjustmentAmount", -1000000,
                                "adjustmentReason", "Tạo phiếu kiểm tra IDOR"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String adjId = com.jayway.jsonpath.JsonPath.read(adjRes, "$.data.requestId");

        String disbRes = mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Phiếu chi kiểm tra IDOR",
                                "category", "Ceremony",
                                "amount", 2000000,
                                "purpose", "Kiểm tra cô lập dòng họ"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String disbId = com.jayway.jsonpath.JsonPath.read(disbRes, "$.data.requestId");

        // Non-existent IDs (including oversized path variable) -> 404
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", "LDG_DOES_NOT_EXIST",
                                "proposedAdjustmentAmount", 1000,
                                "adjustmentReason", "Missing TX"
                        ))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/ledger/adjustments/NON_EXISTENT_ID_LONGER_THAN_20_CHARS")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/ledger/review-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", "LAR_NOT_FOUND", "approved", true))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/ledger/disbursements/NON_EXISTENT_ID_LONGER_THAN_20_CHARS")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/ledger/disbursements/CFD_NOT_FOUND/review").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "confirmPassword", RAW_PASSWORD))))
                .andExpect(status().isNotFound());

        // Non-existent & cross-clan beneficiaryMemberId -> 404
        mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Thụ hưởng không tồn tại",
                                "category", "Scholarship",
                                "amount", 1000000,
                                "beneficiaryMemberId", "MEM_NOT_FOUND",
                                "purpose", "Kiểm tra thành viên không tồn tại"
                        ))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Thụ hưởng dòng họ khác",
                                "category", "Scholarship",
                                "amount", 1000000,
                                "beneficiaryMemberId", MEM_OTHER,
                                "purpose", "Kiểm tra IDOR thành viên"
                        ))))
                .andExpect(status().isNotFound());

        // Cross-tenant access/review attempts from CLAN_OTHER -> 404
        mockMvc.perform(get("/api/v1/ledger/adjustments/" + adjId).with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_OTHER))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/ledger/review-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", adjId, "approved", true))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/ledger/disbursements/" + disbId).with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_OTHER))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/ledger/disbursements/" + disbId + "/review").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "confirmPassword", RAW_PASSWORD))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("QA Group 5: State machine transitions, duplicate pending, locked account & password/reason guards")
    void qaGroup5_stateMachineAndBusinessRuleViolations() throws Exception {
        Authentication headAuth = auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD");
        Authentication lockedHeadAuth = auth(ACC_LOCK, MEM_LOCK, "CLAN_HEAD");

        // 5A. Adjustment duplicate pending & reject missing reason & re-review after rejection
        String adjRes = mockMvc.perform(post("/api/v1/ledger/propose-adjustment")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", TX_BASE,
                                "proposedAdjustmentAmount", -1000000,
                                "adjustmentReason", "Kiểm tra state machine"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String adjId = com.jayway.jsonpath.JsonPath.read(adjRes, "$.data.requestId");

        // Duplicate pending -> 400
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", TX_BASE,
                                "proposedAdjustmentAmount", 500000,
                                "adjustmentReason", "Trùng pending"
                        ))))
                .andExpect(status().isBadRequest());

        // Reject adjustment without reason -> 400
        mockMvc.perform(post("/api/v1/ledger/review-adjustment")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", adjId, "approved", false, "rejectionReason", "   "))))
                .andExpect(status().isBadRequest());

        // Valid reject -> 200
        mockMvc.perform(post("/api/v1/ledger/review-adjustment")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", adjId, "approved", false, "rejectionReason", "Không hợp lệ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adjustmentRequest.approvalStatus").value("Rejected"));

        // Re-approve already Rejected adjustment -> 400
        mockMvc.perform(post("/api/v1/ledger/review-adjustment")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", adjId, "approved", true))))
                .andExpect(status().isBadRequest());

        // 5B. Disbursement password, locked account, path/body mismatch, blank ID, reject & re-review
        String disbRes = mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Kiểm tra state phiếu chi",
                                "category", "ReliefSupport",
                                "amount", 5000000,
                                "purpose", "Kiểm tra mật khẩu và trạng thái"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String disbId = com.jayway.jsonpath.JsonPath.read(disbRes, "$.data.requestId");

        // Approve without confirmPassword -> 400
        mockMvc.perform(post("/api/v1/ledger/disbursements/" + disbId + "/review")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true))))
                .andExpect(status().isBadRequest());

        // Approve with Suspended CLAN_HEAD account (even though BCrypt password matches) -> 400
        mockMvc.perform(post("/api/v1/ledger/disbursements/" + disbId + "/review")
                        .with(authentication(lockedHeadAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "confirmPassword", RAW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("Mật khẩu xác nhận")));

        // Path requestId != body requestId -> 400
        mockMvc.perform(post("/api/v1/ledger/disbursements/" + disbId + "/review")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", "CFD_OTHER", "approved", false, "rejectionReason", "Test"))))
                .andExpect(status().isBadRequest());

        // Flat /review-disbursement with missing requestId -> 400
        mockMvc.perform(post("/api/v1/ledger/review-disbursement")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", false, "rejectionReason", "Thiếu requestId"))))
                .andExpect(status().isBadRequest());

        // Reject missing reason -> 400
        mockMvc.perform(post("/api/v1/ledger/disbursements/" + disbId + "/review")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", false))))
                .andExpect(status().isBadRequest());

        // Approve validly -> 200, then re-reject -> 400
        mockMvc.perform(post("/api/v1/ledger/disbursements/" + disbId + "/review")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "confirmPassword", RAW_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ledger/disbursements/" + disbId + "/review")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", false, "rejectionReason", "Thử hủy sau duyệt"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("QA Group 6: Unauthenticated (401) & strict contextual RBAC (403)")
    void qaGroup6_authenticationAndContextualRbac() throws Exception {
        // 6A. Unauthenticated GET & POST -> 401
        for (String getRoute : List.of(
                "/api/v1/ledger/balance",
                "/api/v1/ledger/verify-integrity",
                "/api/v1/ledger/adjustments",
                "/api/v1/ledger/disbursements")) {
            mockMvc.perform(get(getRoute).header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("originalTransactionId", TX_BASE, "proposedAdjustmentAmount", 100, "adjustmentReason", "X"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "X", "category", "Other", "amount", 100, "purpose", "X"))))
                .andExpect(status().isUnauthorized());

        // 6B. Ordinary MEMBER -> 403 on propose & review
        Authentication memberAuth = auth(ACC_ORD, MEM_ORD, "MEMBER");
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("originalTransactionId", TX_BASE, "proposedAdjustmentAmount", 100, "adjustmentReason", "X"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "X", "category", "Other", "amount", 100, "purpose", "X"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/ledger/review-adjustment").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", "LAR_ANY", "approved", true))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/ledger/review-disbursement").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", "CFD_ANY", "approved", true))))
                .andExpect(status().isForbidden());

        // 6C. EventTreasurer of EVT_A CANNOT propose adjustment on EVT_B or eventless disbursement TX_DISB -> 403
        Authentication treasurerAuth = auth(ACC_TR, MEM_TR, "MEMBER");
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(treasurerAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("originalTransactionId", TX_EVT_B, "proposedAdjustmentAmount", -1000, "adjustmentReason", "Tréo sự kiện B"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(treasurerAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("originalTransactionId", TX_DISB, "proposedAdjustmentAmount", 1000, "adjustmentReason", "Giao dịch không thuộc sự kiện"))))
                .andExpect(status().isForbidden());

        // 6D. BRANCH_HEAD and EventTreasurer CANNOT approve/reject -> 403
        Authentication branchHeadAuth = auth(ACC_BR, MEM_BR, "BRANCH_HEAD");
        for (Authentication nonApprover : List.of(branchHeadAuth, treasurerAuth)) {
            mockMvc.perform(post("/api/v1/ledger/review-adjustment").with(authentication(nonApprover))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("requestId", "LAR_ANY", "approved", true))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/ledger/disbursements/CFD_ANY/review").with(authentication(nonApprover))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("approved", true, "confirmPassword", RAW_PASSWORD))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("QA Group 7: US-61 HTTP PUT/PATCH/DELETE, X-HTTP-Method-Override & CORS preflight restrictions")
    void qaGroup7_immutabilityFilterAndCorsPreflight() throws Exception {
        Authentication adminAuth = auth(ACC_ADMIN, null, "ADMIN");

        List<Supplier<MockHttpServletRequestBuilder>> blocked = List.of(
                () -> put("/api/ledger/" + TX_BASE),
                () -> patch("/api/ledger/" + TX_BASE),
                () -> delete("/api/ledger/" + TX_BASE),
                () -> put("/api/v1/ledger/" + TX_BASE),
                () -> patch("/api/v1/ledger/adjustments/LAR_ANY"),
                () -> delete("/api/v1/ledger/disbursements/CFD_ANY"),
                () -> post("/api/v1/ledger/" + TX_BASE).header("X-HTTP-Method-Override", "DELETE"),
                () -> post("/api/ledger/" + TX_BASE).header("X-HTTP-Method-Override", "PUT")
        );

        for (Supplier<MockHttpServletRequestBuilder> supplier : blocked) {
            mockMvc.perform(supplier.get().with(authentication(adminAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET, POST, HEAD, OPTIONS"))
                    .andExpect(jsonPath("$.code").value(LedgerImmutabilityViolationException.ERROR_CODE));

            mockMvc.perform(supplier.get().header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET, POST, HEAD, OPTIONS"))
                    .andExpect(jsonPath("$.code").value(LedgerImmutabilityViolationException.ERROR_CODE));
        }

        // CORS Preflight: DELETE/PUT/PATCH rejected 403; GET/POST allowed with restricted Allow-Methods
        for (String forbiddenVerb : List.of("PUT", "PATCH", "DELETE")) {
            mockMvc.perform(options("/api/v1/ledger/" + TX_BASE)
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", forbiddenVerb))
                    .andExpect(status().isForbidden());
        }

        String allowMethodsHeader = mockMvc.perform(options("/api/v1/ledger/balance")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Access-Control-Allow-Methods");

        assertThat(allowMethodsHeader).contains("GET", "POST");
        assertThat(allowMethodsHeader).doesNotContain("PUT", "PATCH", "DELETE");
    }

    @Test
    @DisplayName("QA Group 8 (2026-11-19 US-15): Propose aliases, HeadOfOrganizer RBAC, re-propose after rejection/approval, & query filters")
    void qaGroup8_us15ProposeAndQueryAdjustmentsDeepTests() throws Exception {
        Authentication hoAuth = auth(ACC_HO, MEM_HO, "MEMBER");
        Authentication branchHeadAuth = auth(ACC_BR, MEM_BR, "BRANCH_HEAD");
        Authentication headAuth = auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD");
        Authentication memberAuth = auth(ACC_ORD, MEM_ORD, "MEMBER");

        // 8A. BRANCH_HEAD without event role cannot propose adjustment -> 403
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(branchHeadAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", TX_BASE,
                                "proposedAdjustmentAmount", -1000000,
                                "adjustmentReason", "Trưởng chi không phải BTC/Thủ quỹ sự kiện"
                        ))))
                .andExpect(status().isForbidden());

        // 8B. HeadOfOrganizer of EVT_A cannot propose on EVT_B -> 403, CAN propose on EVT_A -> 201
        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(hoAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", TX_EVT_B,
                                "proposedAdjustmentAmount", -1000000,
                                "adjustmentReason", "Không thuộc sự kiện B"
                        ))))
                .andExpect(status().isForbidden());

        // -0.00 -> rejected as zero -> 400
        mockMvc.perform(post("/api/ledger/propose-adjustment").with(authentication(hoAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalTransactionId":"LDG_QA_BASE","proposedAdjustmentAmount":-0.00,"adjustmentReason":"Kiểm tra -0.00"}
                                """))
                .andExpect(status().isBadRequest());

        // snake_case + trimmed original_transaction_id + evidence_attachment_url -> 201
        String snakeRes = mockMvc.perform(post("/api/ledger/propose-adjustment").with(authentication(hoAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "original_transaction_id", "  " + TX_BASE + "  ",
                                "proposed_adjustment_amount", -2000000.00,
                                "adjustment_reason", "  Trùng hóa đơn trang trí  ",
                                "evidence_attachment_url", "   "
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalTransactionId").value(TX_BASE))
                .andExpect(jsonPath("$.data.evidenceAttachmentUrl").doesNotExist())
                .andExpect(jsonPath("$.data.approvalStatus").value("Pending"))
                .andReturn().getResponse().getContentAsString();
        String firstAdjId = com.jayway.jsonpath.JsonPath.read(snakeRes, "$.data.requestId");

        // Reject first proposal, then re-propose on the SAME TX_BASE using alternative @JsonAlias keys (amount, reason, evidenceUrl) -> 201
        mockMvc.perform(post("/api/ledger/review-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", firstAdjId, "approved", false, "rejectionReason", "Cần bổ sung chứng từ"))))
                .andExpect(status().isOk());

        String secondRes = mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(hoAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", TX_BASE,
                                "amount", 1500000.00,
                                "reason", "Hoàn cọc dịch vụ sự kiện A",
                                "evidenceUrl", "https://storage.familyfund.vn/adj/hoan-coc.pdf"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.proposedAdjustmentAmount").value(1500000.00))
                .andExpect(jsonPath("$.data.evidenceAttachmentUrl").value("https://storage.familyfund.vn/adj/hoan-coc.pdf"))
                .andReturn().getResponse().getContentAsString();
        String secondAdjId = com.jayway.jsonpath.JsonPath.read(secondRes, "$.data.requestId");

        // Approve second proposal -> re-proposing a third time after Approved must also succeed -> 201
        mockMvc.perform(post("/api/v1/ledger/review-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("requestId", secondAdjId, "approved", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(hoAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", TX_BASE,
                                "proposedAdjustmentAmount", -500000.00,
                                "adjustmentReason", "Đề xuất lần 3 sau khi đã duyệt lần 2"
                        ))))
                .andExpect(status().isCreated());

        // 8C. Alias GET routes & query param edge cases
        mockMvc.perform(get("/api/ledger/propose-adjustment/" + secondAdjId).with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("Approved"));

        mockMvc.perform(get("/api/ledger/propose-adjustment").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("originalTransactionId", "   ")
                        .param("status", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(3)));

        mockMvc.perform(get("/api/v1/ledger/adjustments").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("originalTransactionId", TX_BASE)
                        .param("status", "Rejected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[0].requestId").value(firstAdjId));

        // Searching by foreign clan TX_OTHER under CLAN_ID must return []
        mockMvc.perform(get("/api/v1/ledger/adjustments").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("originalTransactionId", TX_OTHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // Searching non-existent TX filter returns []
        mockMvc.perform(get("/api/v1/ledger/adjustments").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("originalTransactionId", "LDG_NO_SUCH_TX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // Whitespace path ID -> 404 (use URI.create to avoid MockMvc double-encoding %20 -> %2520);
        // omitting X-Clan-Id defaults to CLAN_DEFAULT and isolates CLN_QA_01 data
        mockMvc.perform(get(URI.create("/api/v1/ledger/adjustments/%20%20")).with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/ledger/adjustments/" + firstAdjId).with(authentication(memberAuth)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("QA Group 9 (2026-11-22 US-59): Review aliases, ADMIN review, stale rejectionReason clearing & interleaved US-59/US-60 balance guard")
    void qaGroup9_us59ReviewAdjustmentDeepAndInterleavedFlows() throws Exception {
        Authentication adminAuth = auth(ACC_ADMIN, MEM_ADMIN, "ADMIN");
        Authentication headAuth = auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD");
        Authentication treasurerAuth = auth(ACC_TR, MEM_TR, "MEMBER");

        // 9A. Propose -60M adjustment while balance is 100M
        String prop60Res = mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(treasurerAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", TX_BASE,
                                "proposedAdjustmentAmount", -60000000.00,
                                "adjustmentReason", "Giảm trừ chi phí lớn"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String prop60Id = com.jayway.jsonpath.JsonPath.read(prop60Res, "$.data.requestId");

        // 9B. Interleaved US-60 disbursement of 50M approved -> balance drops from 100M to 50M
        String disb50Res = mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Chi cứu trợ xen kẽ 50tr",
                                "category", "ReliefSupport",
                                "amount", 50000000.00,
                                "purpose", "Chi xen kẽ kiểm tra khóa số dư"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String disb50Id = com.jayway.jsonpath.JsonPath.read(disb50Res, "$.data.requestId");

        mockMvc.perform(post("/api/v1/ledger/disbursements/" + disb50Id + "/review").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "confirmPassword", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(50000000.00));

        // 9C. Approving the -60M adjustment now would make balance -10M -> must block 400 and stay Pending
        mockMvc.perform(post("/api/v1/ledger/review-adjustment").with(authentication(adminAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("request_id", prop60Id, "is_approved", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("bị âm")));

        mockMvc.perform(get("/api/v1/ledger/adjustments/" + prop60Id).with(authentication(adminAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("Pending"));

        // 9D. ADMIN rejects -60M via snake_case aliases
        mockMvc.perform(post("/api/ledger/review-adjustment").with(authentication(adminAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "request_id", prop60Id,
                                "is_approved", false,
                                "rejection_reason", "Không đủ số dư sau khi đã chi 50tr"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adjustmentRequest.approvalStatus").value("Rejected"))
                .andExpect(jsonPath("$.data.adjustmentRequest.reviewedByAccountId").value(ACC_ADMIN));

        // 9E. Drain remaining 50M to 0.00, then ADMIN approves a positive +15M adjustment using default description and stale rejectionReason
        String disbRestRes = mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Chi hết 50tr còn lại",
                                "category", "Other",
                                "amount", 50000000.00,
                                "purpose", "Đưa số dư về 0.00"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String disbRestId = com.jayway.jsonpath.JsonPath.read(disbRestRes, "$.data.requestId");

        mockMvc.perform(post("/api/v1/ledger/disbursements/" + disbRestId + "/review").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "confirmPassword", RAW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(0.00));

        String prop15Res = mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(adminAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "originalTransactionId", TX_BASE,
                                "proposedAdjustmentAmount", 15000000.00,
                                "adjustmentReason", "Bù trừ dương khôi phục quỹ"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String prop15Id = com.jayway.jsonpath.JsonPath.read(prop15Res, "$.data.requestId");

        mockMvc.perform(post("/api/v1/ledger/review-adjustment").with(authentication(adminAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "requestId", prop15Id,
                                "approved", true,
                                "rejectionReason", "Lý do thừa phải bị xóa sạch",
                                "review_note", "   "
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adjustmentRequest.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(15000000.00))
                .andExpect(jsonPath("$.data.ledgerEntry.description", Matchers.containsString("Bù trừ giao dịch " + TX_BASE)));

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/ledger/verify-integrity").with(authentication(adminAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalEntries").value(6))
                .andExpect(jsonPath("$.data.verifiedEntries").value(6));
    }

    @Test
    @DisplayName("QA Group 10 (2026-11-22 US-60): Balance lifecycle, beneficiary resolution, all categories/filters & password/account guards")
    void qaGroup10_us60CommonFundDisbursementDeepTests() throws Exception {
        Authentication headAuth = auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD");
        Authentication deactAuth = auth(ACC_DEACT, MEM_DEACT, "CLAN_HEAD");
        Authentication pendAuth = auth(ACC_PEND, MEM_PEND, "CLAN_HEAD");
        Authentication treasurerAuth = auth(ACC_TR, MEM_TR, "MEMBER");
        Authentication memberAuth = auth(ACC_ORD, MEM_ORD, "MEMBER");

        // 10A. Beneficiary resolution variations + all expense categories & snake_case aliases
        String p1 = mockMvc.perform(post("/api/ledger/disbursements").with(authentication(treasurerAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Học bổng tự điền tên",
                                "expense_category", "Scholarship",
                                "amount", 30000000.00,
                                "beneficiary_member_id", MEM_ORD,
                                "reason", "Thưởng sinh viên",
                                "attachment_url", "https://storage.familyfund.vn/hb1.pdf"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.beneficiaryName").value("Thành Viên QA"))
                .andExpect(jsonPath("$.data.beneficiaryMemberId").value(MEM_ORD))
                .andExpect(jsonPath("$.data.ledgerTransactionId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String reqId1 = com.jayway.jsonpath.JsonPath.read(p1, "$.data.requestId");

        String p2 = mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Hỗ trợ ưu tiên tên chỉ định",
                                "category", "ReliefSupport",
                                "amount", 40000000.00,
                                "beneficiaryMemberId", MEM_ORD,
                                "beneficiaryName", "Gia đình cháu QA (Chỉ định)",
                                "purpose", "Ưu tiên explicit name"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.beneficiaryName").value("Gia đình cháu QA (Chỉ định)"))
                .andReturn().getResponse().getContentAsString();
        String reqId2 = com.jayway.jsonpath.JsonPath.read(p2, "$.data.requestId");

        mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Chi khác không người nhận",
                                "category", "Other",
                                "amount", 5000000.00,
                                "beneficiaryName", "   ",
                                "beneficiaryMemberId", "   ",
                                "purpose", "Kiểm tra blank -> null"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.beneficiaryName").doesNotExist())
                .andExpect(jsonPath("$.data.beneficiaryMemberId").doesNotExist());

        // 10B. Pending sum on GET /balance: 30M + 40M + 5M = 75M; availableBalance remains 100M
        mockMvc.perform(get("/api/v1/ledger/balance").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalance").value(100000000.00))
                .andExpect(jsonPath("$.data.pendingDisbursementAmount").value(75000000.00));

        // 10C. Deactivated & PendingApproval CLAN_HEAD accounts blocked even with valid password -> 400
        for (Authentication inactiveHead : List.of(deactAuth, pendAuth)) {
            mockMvc.perform(post("/api/v1/ledger/disbursements/" + reqId1 + "/review")
                            .with(authentication(inactiveHead))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("approved", true, "confirmPassword", RAW_PASSWORD))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", Matchers.containsString("Mật khẩu xác nhận")));
        }

        // Cross-clan EventTreasurer (only treasurer in CLN_QA_01) trying to propose in CLAN_OTHER -> 403
        mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(treasurerAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Thử vượt quyền sang dòng họ khác",
                                "category", "Other",
                                "amount", 1000000,
                                "purpose", "Cross-clan treasurer check"
                        ))))
                .andExpect(status().isForbidden());

        // Wrong password on non-existent requestId returns 400 (password verified before DB lookup), valid password returns 404
        mockMvc.perform(post("/api/v1/ledger/disbursements/CFD_GHOST/review").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "confirmPassword", "WrongPass"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/ledger/disbursements/CFD_GHOST/review").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "confirmPassword", RAW_PASSWORD))))
                .andExpect(status().isNotFound());

        // Whitespace path variables -> 404 on GET, 400 on review (via URI.create to bypass MockMvc template encoding)
        mockMvc.perform(get(URI.create("/api/v1/ledger/disbursements/%20%20")).with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(URI.create("/api/v1/ledger/disbursements/%20%20/review")).with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approved", true, "confirmPassword", RAW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("Mã đề xuất")));

        // Reject reqId2 without password (even sending confirmPassword="wrong") -> 200 Rejected
        mockMvc.perform(post("/api/ledger/review-disbursement").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "request_id", reqId2,
                                "is_approved", false,
                                "confirm_password", "ignored_when_rejecting",
                                "rejection_reason", "Khoản chi chưa cần thiết"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbursementRequest.approvalStatus").value("Rejected"))
                .andExpect(jsonPath("$.data.disbursementRequest.ledgerTransactionId").doesNotExist());

        // Approve reqId1 via snake_case aliases + leading/trailing space password + stale rejectionReason -> clears rejectionReason
        String app1 = mockMvc.perform(post("/api/v1/ledger/review-disbursement").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "request_id", reqId1,
                                "is_approved", true,
                                "confirm_password", " " + RAW_PASSWORD + " ",
                                "rejection_reason", "Sẽ bị xóa khi duyệt",
                                "review_note", "Duyệt giải ngân học bổng 2026"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbursementRequest.approvalStatus").value("Approved"))
                .andExpect(jsonPath("$.data.disbursementRequest.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(70000000.00))
                .andReturn().getResponse().getContentAsString();
        String approvedTxId = com.jayway.jsonpath.JsonPath.read(app1, "$.data.ledgerEntry.transactionId");

        // Balance after rejecting 40M & approving 30M: available=70M, pending=5M, latestTransactionId=approvedTxId
        mockMvc.perform(get("/api/ledger/balance").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalance").value(70000000.00))
                .andExpect(jsonPath("$.data.pendingDisbursementAmount").value(5000000.00))
                .andExpect(jsonPath("$.data.latestTransactionId").value(approvedTxId));

        // Query filters: Approved + Scholarship -> 1 match with ledgerTransactionId populated; empty params return all 4 clan requests
        mockMvc.perform(get("/api/ledger/disbursements").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("status", "Approved")
                        .param("category", "Scholarship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[0].requestId").value(reqId1))
                .andExpect(jsonPath("$.data[0].ledgerTransactionId").value(approvedTxId));

        mockMvc.perform(get("/api/v1/ledger/disbursements").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("status", "")
                        .param("category", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(4)));
    }

    @Test
    @DisplayName("QA Group 11: Adversarial JSON shapes (null/array/scalar/NaN/1e50), extra unknown fields & dual-route parity")
    void qaGroup11_adversarialSerializationAndDualRouteParity() throws Exception {
        Authentication headAuth = auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD");

        List<String> postRoutes = List.of(
                "/api/v1/ledger/propose-adjustment",
                "/api/v1/ledger/review-adjustment",
                "/api/v1/ledger/disbursements",
                "/api/v1/ledger/review-disbursement"
        );

        // Literal "null", JSON array "[]", JSON scalar "123" must return 400 (never 500)
        for (String route : postRoutes) {
            for (String badJsonShape : List.of("null", "[]", "123", "\"hello\"")) {
                mockMvc.perform(post(route).with(authentication(headAuth))
                                .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(badJsonShape))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.success").value(false));
            }
        }

        // Scientific extreme overflow & NaN on monetary numbers -> 400
        mockMvc.perform(post("/api/v1/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Test overflow","category":"Other","amount":1e50,"purpose":"Test"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/ledger/propose-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalTransactionId":"LDG_QA_BASE","proposedAdjustmentAmount":"NaN","adjustmentReason":"Test"}
                                """))
                .andExpect(status().isBadRequest());

        // Invalid boolean shapes {} / [] -> 400 Bad Request;
        // Note: Jackson default scalar coercion turns numeric 123 into Boolean.TRUE (reaches service -> 404 Not Found on probe ID)
        for (String badBool : List.of("{}", "[]")) {
            mockMvc.perform(post("/api/v1/ledger/review-adjustment").with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"requestId":"LAR_TYPE_PROBE","approved":%s}
                                    """.formatted(badBool)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/ledger/review-adjustment").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"LAR_TYPE_PROBE","approved":123}
                                """))
                .andExpect(status().isNotFound());

        // Unknown extra JSON fields are ignored cleanly without 500
        mockMvc.perform(post("/api/ledger/disbursements").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Phiếu chi có trường thừa",
                                    "category": "Ceremony",
                                    "amount": 1000000.00,
                                    "purpose": "Kiểm tra ignore unknown JSON fields",
                                    "unexpectedExtraField": "should-be-ignored"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.approvalStatus").value("Pending"));

        // Dual-route parity on /api/ledger/verify-integrity & /api/v1/ledger/verify-integrity
        mockMvc.perform(get("/api/ledger/verify-integrity").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalEntries").value(3));

        mockMvc.perform(get("/api/v1/ledger/verify-integrity").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalEntries").value(3));
    }
}
