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
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.entity.ImmutableLedger;
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
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LedgerAnnualAuditQaIntegrationTest {

    private static final String CLAN_A = "CLN_AQA_A";
    private static final String CLAN_B = "CLN_AQA_B";
    private static final String CLAN_EMPTY = "CLN_AQA_E";
    private static final String CLAN_GHOST = "CLN_AQA_GHOST";

    private static final String BRANCH_A = "BR_AQA_A";
    private static final String BRANCH_B = "BR_AQA_B";
    private static final String BRANCH_E = "BR_AQA_E";

    private static final String EVT_A = "EVT_AQA_A";
    private static final String TX_A_BASE = "LDG_AQA_BASE";
    private static final String TX_B_BASE = "LDG_BQA_BASE";
    private static final String RAW_PASSWORD = "QaPassword@123";

    private static final String MEM_A_HEAD = "MEM_AQA_HD";
    private static final String ACC_A_HEAD = "ACC_AQA_HD";

    private static final String MEM_A_TREAS = "MEM_AQA_TR";
    private static final String ACC_A_TREAS = "ACC_AQA_TR";

    private static final String MEM_A_ORD = "MEM_AQA_OR";
    private static final String ACC_A_ORD = "ACC_AQA_OR";

    private static final String MEM_A_ADMIN = "MEM_AQA_AD";
    private static final String ACC_A_ADMIN = "ACC_AQA_AD";

    private static final String MEM_B_HEAD = "MEM_BQA_HD";
    private static final String ACC_B_HEAD = "ACC_BQA_HD";

    private static final String MEM_E_HEAD = "MEM_EQA_HD";
    private static final String ACC_E_HEAD = "ACC_EQA_HD";

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
    private ImmutableLedgerRepository immutableLedgerRepository;

    @Autowired
    private ImmutableLedgerHashCalculator ledgerHashCalculator;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    private Instant baseTime;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN_A);

        clanRepository.save(Clan.builder().clanId(CLAN_A).clanName("Dòng họ QA Chính").build());
        clanRepository.save(Clan.builder().clanId(CLAN_B).clanName("Dòng họ QA Khác").build());
        clanRepository.save(Clan.builder().clanId(CLAN_EMPTY).clanName("Dòng họ QA Trống").build());

        FamilyBranch brA = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_A).clanId(CLAN_A).branchName("Chi A").generationLevel(1).build());
        FamilyBranch brB = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_B).clanId(CLAN_B).branchName("Chi B").generationLevel(1).build());
        FamilyBranch brE = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_E).clanId(CLAN_EMPTY).branchName("Chi E").generationLevel(1).build());

        Member mAHead = memberRepository.save(Member.builder()
                .memberId(MEM_A_HEAD).branch(brA).fullName("Cụ Trưởng Tộc A").gender("Male").generation(1).status("Living").build());
        Member mATreas = memberRepository.save(Member.builder()
                .memberId(MEM_A_TREAS).branch(brA).fullName("Thủ Quỹ A").gender("Female").generation(2).status("Living").build());
        Member mAOrd = memberRepository.save(Member.builder()
                .memberId(MEM_A_ORD).branch(brA).fullName("Thành Viên A").gender("Male").generation(3).status("Living").build());
        Member mAAdmin = memberRepository.save(Member.builder()
                .memberId(MEM_A_ADMIN).branch(brA).fullName("Quản Trị Viên A").gender("Male").generation(2).status("Living").build());
        Member mBHead = memberRepository.save(Member.builder()
                .memberId(MEM_B_HEAD).branch(brB).fullName("Cụ Trưởng Tộc B").gender("Male").generation(1).status("Living").build());
        Member mEHead = memberRepository.save(Member.builder()
                .memberId(MEM_E_HEAD).branch(brE).fullName("Cụ Trưởng Tộc E").gender("Male").generation(1).status("Living").build());

        String encoded = passwordEncoder.encode(RAW_PASSWORD);
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_A_HEAD).memberId(mAHead.getMemberId()).phoneNumber("0988000001")
                .passwordHash(encoded).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_A_TREAS).memberId(mATreas.getMemberId()).phoneNumber("0988000002")
                .passwordHash(encoded).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_A_ORD).memberId(mAOrd.getMemberId()).phoneNumber("0988000003")
                .passwordHash(encoded).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_A_ADMIN).memberId(mAAdmin.getMemberId()).phoneNumber("0988000004")
                .passwordHash(encoded).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_B_HEAD).memberId(mBHead.getMemberId()).phoneNumber("0988000005")
                .passwordHash(encoded).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_E_HEAD).memberId(mEHead.getMemberId()).phoneNumber("0988000006")
                .passwordHash(encoded).accountStatus(AccountStatus.Active).build());

        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_A_HEAD, SystemRoles.CLAN_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_A_ADMIN, SystemRoles.ADMIN)).build());
        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_B_HEAD, SystemRoles.CLAN_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_E_HEAD, SystemRoles.CLAN_HEAD)).build());

        eventRepository.save(Event.builder()
                .eventId(EVT_A).clanId(CLAN_A).eventName("Đại Lễ QA 2026")
                .eventDatetime(Instant.now().minus(10, ChronoUnit.DAYS)).location("Từ đường A")
                .createdBy(ACC_A_HEAD).status(EventStatus.Ended).build());
        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVT_A).memberId(MEM_A_TREAS).eventRole(EventRole.EventTreasurer).isReadOnly(false).build());

        baseTime = Instant.now().minus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        String hashA = ledgerHashCalculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH,
                TX_A_BASE, CLAN_A, EVT_A, null, null, null,
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"), new BigDecimal("100000000.00"),
                "Kết dư Đại Lễ QA 2026", ACC_A_TREAS, ACC_A_HEAD, baseTime
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(TX_A_BASE).clanId(CLAN_A).eventId(EVT_A)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00"))
                .runningBalance(new BigDecimal("100000000.00"))
                .description("Kết dư Đại Lễ QA 2026")
                .createdByAccountId(ACC_A_TREAS).approvedByAccountId(ACC_A_HEAD)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH).recordHash(hashA)
                .createdAt(baseTime).build());

        String hashB = ledgerHashCalculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH,
                TX_B_BASE, CLAN_B, null, null, null, null,
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("35000000.00"), new BigDecimal("35000000.00"),
                "Kết dư Họ B", ACC_B_HEAD, ACC_B_HEAD, baseTime
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(TX_B_BASE).clanId(CLAN_B)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("35000000.00"))
                .runningBalance(new BigDecimal("35000000.00"))
                .description("Kết dư Họ B")
                .createdByAccountId(ACC_B_HEAD).approvedByAccountId(ACC_B_HEAD)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH).recordHash(hashB)
                .createdAt(baseTime).build());
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

    private SQLException assertSqlFailureWithSavepoint(String sql, SqlBinder binder) throws SQLException {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        Savepoint sp = conn.setSavepoint();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(ps);
            }
            ps.execute();
            throw new AssertionError("Expected PostgreSQL to reject SQL statement: " + sql);
        } catch (SQLException ex) {
            conn.rollback(sp);
            conn.releaseSavepoint(sp);
            return ex;
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    @Test
    @DisplayName("QA Group 1: Auth, RBAC, Wrong HTTP Verbs & Immutability Filter (PUT/PATCH/DELETE + Method Override)")
    void qaGroup1_authRbacWrongVerbsAndImmutabilityFilter() throws Exception {
        // 1A. Anonymous caller -> 401
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ledger/annual-audits"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ledger/annual-audits/AUD_ANY"))
                .andExpect(status().isUnauthorized());

        // 1B. MEMBER & BRANCH_HEAD cannot POST -> 403
        for (Authentication caller : List.of(
                auth(ACC_A_ORD, MEM_A_ORD, "MEMBER"),
                auth(ACC_A_TREAS, MEM_A_TREAS, "BRANCH_HEAD")
        )) {
            for (String postUrl : List.of("/api/ledger/annual-audit", "/api/v1/ledger/annual-audit")) {
                mockMvc.perform(post(postUrl)
                                .with(authentication(caller))
                                .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2026}"))
                        .andExpect(status().isForbidden());
            }
        }

        // 1C. ADMIN allowed to POST /annual-audit -> 201 Created, then MEMBER & BRANCH_HEAD allowed to GET -> 200 OK
        String createdRes = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACC_A_ADMIN, MEM_A_ADMIN, "ADMIN")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditorAccountId").value(ACC_A_ADMIN))
                .andExpect(jsonPath("$.data.auditorDisplayName").value("Quản Trị Viên A"))
                .andReturn().getResponse().getContentAsString();
        String auditId = JsonPath.read(createdRes, "$.data.auditId");

        for (Authentication reader : List.of(
                auth(ACC_A_ORD, MEM_A_ORD, "MEMBER"),
                auth(ACC_A_TREAS, MEM_A_TREAS, "BRANCH_HEAD"),
                auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD"),
                auth(ACC_A_ADMIN, MEM_A_ADMIN, "ADMIN")
        )) {
            mockMvc.perform(get("/api/ledger/annual-audits")
                            .with(authentication(reader))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].auditId").value(auditId));

            mockMvc.perform(get("/api/v1/ledger/annual-audits/" + auditId)
                            .with(authentication(reader))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.auditId").value(auditId));
        }

        // 1D. Wrong HTTP verbs -> 405
        mockMvc.perform(get("/api/ledger/annual-audit")
                        .with(authentication(auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/ledger/annual-audits")
                        .with(authentication(auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        // 1E. Immutability HTTP Guard (PUT/PATCH/DELETE + X-HTTP-Method-Override) even for ADMIN -> 405 LEDGER_IMMUTABILITY_VIOLATION
        List<Supplier<MockHttpServletRequestBuilder>> mutators = List.of(
                () -> put("/api/ledger/annual-audit"),
                () -> patch("/api/ledger/annual-audit"),
                () -> delete("/api/ledger/annual-audit"),
                () -> put("/api/v1/ledger/annual-audit"),
                () -> patch("/api/ledger/annual-audits"),
                () -> delete("/api/v1/ledger/annual-audits/" + auditId),
                () -> post("/api/ledger/annual-audit").header("X-HTTP-Method-Override", "DELETE"),
                () -> post("/api/v1/ledger/annual-audits/" + auditId).header("X-HTTP-Method-Override", "PUT")
        );
        for (Supplier<MockHttpServletRequestBuilder> mut : mutators) {
            mockMvc.perform(mut.get()
                            .with(authentication(auth(ACC_A_ADMIN, MEM_A_ADMIN, "ADMIN")))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET, POST, HEAD, OPTIONS"))
                    .andExpect(jsonPath("$.code").value(LedgerImmutabilityViolationException.ERROR_CODE));
        }
    }

    @Test
    @DisplayName("QA Group 2: Wrong Data Types, Malformed JSON, Media Type & Invalid Enum Params")
    void qaGroup2_wrongTypesMalformedJsonAndUnsupportedMediaTypes() throws Exception {
        Authentication headAuth = auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD");

        // 2A. Malformed JSON, empty body, JSON array, JSON null literal -> 400
        for (String badJson : List.of("{\"actualBalance\": ", "", "[]", "null")) {
            mockMvc.perform(post("/api/ledger/annual-audit")
                            .with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badJson))
                    .andExpect(status().isBadRequest());
        }

        // 2B. Unsupported media type -> 415
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("actualBalance=100000000"))
                .andExpect(status().isUnsupportedMediaType());

        // 2C. Wrong field types in JSON body -> 400
        for (String badBody : List.of(
                "{\"actualBalance\": \"khong-hop-le\"}",
                "{\"actualBalance\": true}",
                "{\"actualBalance\": {}}",
                "{\"actualBalance\": 100000000, \"auditYear\": \"hai-nghin\"}",
                "{\"actualBalance\": 100000000, \"auditYear\": 2147483648}",
                "{\"actualBalance\": 100000000, \"auditYear\": true}",
                "{\"actualBalance\": 100000000, \"notes\": []}"
        )) {
            mockMvc.perform(post("/api/ledger/annual-audit")
                            .with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badBody))
                    .andExpect(status().isBadRequest());
        }

        // 2D. Invalid auditYear query param on GET -> 400
        for (String badYear : List.of("abc", "2026.5", "99999999999999", "true")) {
            mockMvc.perform(get("/api/ledger/annual-audits")
                            .param("auditYear", badYear)
                            .with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                    .andExpect(status().isBadRequest());
        }

        // 2E. Invalid status enum query param -> 400 with clean message
        for (String badStatus : List.of("INVALID", "matched", "MATCHED", "123")) {
            mockMvc.perform(get("/api/ledger/annual-audits")
                            .param("status", badStatus)
                            .with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.containsString("[Ljava.lang.String;@"))))
                    .andExpect(jsonPath("$.message").value(Matchers.containsString("Matched")))
                    .andExpect(jsonPath("$.message").value(Matchers.containsString("DiscrepancyDetected")));
        }

        mockMvc.perform(get("/api/ledger/annual-audits")
                        .param("status", "Matched")
                        .param("status", "INVALID")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.containsString("[Ljava.lang.String;@"))));

        mockMvc.perform(get("/api/ledger/annual-audits")
                        .param("auditYear", "2026")
                        .param("auditYear", "abc")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.containsString("[Ljava.lang.String;@"))));
    }

    @Test
    @DisplayName("QA Group 3: Null, Empty, Blank, Boundaries & UTF-16 Surrogate Safe Truncation")
    void qaGroup3_nullEmptyBlankBoundariesAndUtf16Truncation() throws Exception {
        Authentication headAuth = auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD");

        // 3A. Null/missing/negative/over-precision/over-digit actualBalance -> 400
        for (String invalidAmountBody : List.of(
                "{}",
                "{\"actualBalance\": null}",
                "{\"actualBalance\": -0.01}",
                "{\"actualBalance\": -5000000.00}",
                "{\"actualBalance\": 100.001}",
                "{\"actualBalance\": 10000000000000000.00}"
        )) {
            mockMvc.perform(post("/api/ledger/annual-audit")
                            .with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidAmountBody))
                    .andExpect(status().isBadRequest());
        }

        // 3B. auditYear outside [2000, 2100] on both POST and GET -> 400
        for (int badYear : List.of(1999, 2101, 0, -2026)) {
            mockMvc.perform(post("/api/ledger/annual-audit")
                            .with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualBalance\": 100000000.00, \"auditYear\": " + badYear + "}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get("/api/ledger/annual-audits")
                            .param("auditYear", String.valueOf(badYear))
                            .with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                    .andExpect(status().isBadRequest());
        }

        // 3C. Length boundary failures (>20 refTxId, >2000 notes, >500 url) -> 400
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"referenceTransactionId\": \"" + "A".repeat(21) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"notes\": \"" + "N".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"evidenceAttachmentUrl\": \"" + "U".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());

        // 3D. Exact inclusive year boundaries (2000 & 2100), whitespace normalization, unknown fields, different scale (100000000 vs 100000000.0)
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualBalance": 100000000,
                                  "auditYear": 2000,
                                  "referenceTransactionId": "   ",
                                  "notes": "   ",
                                  "evidenceAttachmentUrl": "   ",
                                  "unknownExtraField": "ignored"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("Matched"))
                .andExpect(jsonPath("$.data.auditYear").value(2000))
                .andExpect(jsonPath("$.data.nextPeriodYear").value(2001))
                .andExpect(jsonPath("$.data.notes").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.evidenceAttachmentUrl").value(Matchers.nullValue()));

        // Omitted auditYear -> defaults to current Vietnam year
        int currentVnYear = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear();
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditYear").value(currentVnYear))
                .andExpect(jsonPath("$.data.nextPeriodYear").value(currentVnYear + 1));

        // 3E. Exact 2000-char notes containing 4-byte emoji surrogate pairs ("🎉".repeat(1000)) on Discrepancy (Defect #2 verification!)
        String surrogateNotes = "🎉".repeat(1000);
        String url500 = "https://cdn.example.com/" + "a".repeat(500 - "https://cdn.example.com/".length());
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualBalance": 95000000.00,
                                  "auditYear": 2100,
                                  "notes": "%s",
                                  "evidenceAttachmentUrl": "%s"
                                }
                                """.formatted(surrogateNotes, url500)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("DiscrepancyDetected"))
                .andExpect(jsonPath("$.data.auditYear").value(2100));

        // Empty GET query params -> 200 OK
        mockMvc.perform(get("/api/ledger/annual-audits")
                        .param("auditYear", "")
                        .param("status", "")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(3)));
    }

    @Test
    @DisplayName("QA Group 4: Non-Existent Clan/Transaction/Audit References & Empty Ledger Clan Edge Cases")
    void qaGroup4_nonExistentReferencesAndEmptyLedger() throws Exception {
        Authentication headA = auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD");
        Authentication headE = auth(ACC_E_HEAD, MEM_E_HEAD, "CLAN_HEAD");

        // 4A. Non-existent X-Clan-Id on POST -> 404 Not Found (Defect #1 fix verified!)
        for (String balance : List.of("0.00", "5000000.00")) {
            mockMvc.perform(post("/api/ledger/annual-audit")
                            .with(authentication(headA))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_GHOST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualBalance\": " + balance + ", \"auditYear\": 2026}"))
                    .andExpect(status().isNotFound());
        }

        // Non-existent X-Clan-Id on GET list -> empty [], on GET by ID -> 404
        mockMvc.perform(get("/api/ledger/annual-audits")
                        .with(authentication(headA))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_GHOST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // 4B. Non-existent referenceTransactionId -> 404
        for (String balance : List.of("100000000.00", "95000000.00")) {
            mockMvc.perform(post("/api/ledger/annual-audit")
                            .with(authentication(headA))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualBalance\": " + balance + ", \"auditYear\": 2026, \"referenceTransactionId\": \"LDG_NOT_EXIST\"}"))
                    .andExpect(status().isNotFound());
        }

        // 4C. Non-existent or blank auditId -> 404
        for (String badAuditId : List.of("AUD_NOT_EXIST", "%20%20%20")) {
            mockMvc.perform(get(URI.create("/api/ledger/annual-audits/" + badAuditId))
                            .with(authentication(headA))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                    .andExpect(status().isNotFound());
        }

        // 4D. Empty ledger clan: actual > 0 -> 400 BusinessRuleException
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headE))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_EMPTY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 5000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("chưa có bút toán gốc")));

        // 4E. Empty ledger clan: actual = 0.00 -> 201 Matched + GENESIS OpeningBalanceCarryForward + integrity valid
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headE))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_EMPTY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 0.00, \"auditYear\": 2026}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("Matched"))
                .andExpect(jsonPath("$.data.systemBalance").value(0.00))
                .andExpect(jsonPath("$.data.openingBalanceNextPeriod").value(0.00))
                .andExpect(jsonPath("$.data.referenceTransactionId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.closingLedgerEntry.previousHash").value("GENESIS"))
                .andExpect(jsonPath("$.data.closingLedgerEntry.transactionType").value("OpeningBalanceCarryForward"));

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/ledger/verify-integrity")
                        .with(authentication(headE))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_EMPTY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalEntries").value(1));
    }

    @Test
    @DisplayName("QA Group 5: JSON Aliases & Dual Route Parity (/api/ledger vs /api/v1/ledger)")
    void qaGroup5_jsonAliasesAndDualRouteParity() throws Exception {
        Authentication headAuth = auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD");

        // Test snake_case & alternative @JsonAlias names + padded referenceTransactionId
        mockMvc.perform(post("/api/v1/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "countedBalance": 100000000.00,
                                  "fiscalYear": 2022,
                                  "originalTransactionId": "  %s  ",
                                  "note": "Alias check 1",
                                  "evidenceUrl": "https://cdn.example/alias1.pdf"
                                }
                                """.formatted(TX_A_BASE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditYear").value(2022))
                .andExpect(jsonPath("$.data.referenceTransactionId").value(TX_A_BASE))
                .andExpect(jsonPath("$.data.notes").value("Alias check 1"));

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualCashBalance": 100000000.00,
                                  "year": 2023,
                                  "reason": "Alias check 2"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditYear").value(2023))
                .andExpect(jsonPath("$.data.notes").value("Alias check 2"));

        mockMvc.perform(get("/api/v1/ledger/annual-audits")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(2)));
    }

    @Test
    @DisplayName("QA Group 6: Multi-Year Rollover & Discrepancy -> Reject US-59 -> Re-Audit Match Lifecycle")
    void qaGroup6_multiYearRolloverAndRejectAdjustmentReAuditLifecycle() throws Exception {
        Authentication headAuth = auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD");

        // Year 2025: Match at 100M -> creates OpeningBalanceCarryForward as latest ledger entry
        String m2025 = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2025}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("Matched"))
                .andReturn().getResponse().getContentAsString();
        String closing2025TxId = JsonPath.read(m2025, "$.data.closingTransactionId");

        // Year 2026 attempt 1: Discrepancy 92M (-8M), defaults referenceTransactionId to closing2025TxId
        String d2026 = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 92000000.00, \"auditYear\": 2026, \"notes\": \"Đếm thiếu phong bì\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("DiscrepancyDetected"))
                .andExpect(jsonPath("$.data.referenceTransactionId").value(closing2025TxId))
                .andReturn().getResponse().getContentAsString();
        String adjReqId = JsonPath.read(d2026, "$.data.adjustmentRequestId");

        // Reject adjustment (counted again, money found in envelope)
        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "approved": false,
                                  "rejectionReason": "Kiểm đếm sót tiền mặt trong phong bì"
                                }
                                """.formatted(adjReqId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adjustmentRequest.approvalStatus").value("Rejected"));

        // Year 2026 attempt 2: Match at 100M
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("Matched"))
                .andExpect(jsonPath("$.data.nextPeriodYear").value(2027));

        entityManager.flush();
        entityManager.clear();

        // History for 2026 shows 2 records (Matched first, Rejected Discrepancy second)
        mockMvc.perform(get("/api/ledger/annual-audits")
                        .param("auditYear", "2026")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.data[0].auditStatus").value("Matched"))
                .andExpect(jsonPath("$.data[1].auditStatus").value("DiscrepancyDetected"))
                .andExpect(jsonPath("$.data[1].autoGeneratedAdjustmentRequest.approvalStatus").value("Rejected"))
                .andExpect(jsonPath("$.data[1].autoGeneratedAdjustmentRequest.rejectionReason")
                        .value("Kiểm đếm sót tiền mặt trong phong bì"));

        // Hash chain integrity remains 100% valid across 3 entries
        mockMvc.perform(get("/api/v1/ledger/verify-integrity")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalEntries").value(3));
    }

    @Test
    @DisplayName("QA Group 7: Deficit (-15M) + Surplus (+5M) Reconciliations Do Not Distort US-62 Cash-Flow Totals")
    void qaGroup7_deficitAndSurplusReconciliations_zeroDistortionInUs62() throws Exception {
        Authentication headAuth = auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD");

        // 2026 Deficit: 85M vs 100M (-15M) -> Approve -> Re-audit Match
        String r1 = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 85000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String lar1 = JsonPath.read(r1, "$.data.adjustmentRequestId");
        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\": \"" + lar1 + "\", \"approved\": true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 85000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("Matched"));

        // 2027 Surplus: 90M vs 85M (+5M) -> Approve -> Re-audit Match
        String r2 = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 90000000.00, \"auditYear\": 2027}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String lar2 = JsonPath.read(r2, "$.data.adjustmentRequestId");
        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\": \"" + lar2 + "\", \"approved\": true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 90000000.00, \"auditYear\": 2027}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("Matched"));

        entityManager.flush();
        entityManager.clear();

        // US-62 cash flow: Inflow = 100M + 5M = 105M, Outflow = 15M, Net = 90M (0.00 carry-forwards add 0!)
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.summary.currentFundBalance").value(90000000.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalInflow").value(105000000.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalOutflow").value(15000000.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(90000000.00));

        // Filter OpeningBalanceCarryForward -> 2 entries, both isAdjustment=false
        mockMvc.perform(get("/api/ledger")
                        .param("transactionType", "OpeningBalanceCarryForward")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].isAdjustment").value(false))
                .andExpect(jsonPath("$.data.items[1].isAdjustment").value(false));
    }

    @Test
    @DisplayName("QA Group 8: Pending Guards (US-15, US-60, and Both Simultaneously) + Duplicate 409 Conflict")
    void qaGroup8_pendingGuardsAndDuplicateConflict() throws Exception {
        Authentication headAuth = auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD");
        Authentication treasAuth = auth(ACC_A_TREAS, MEM_A_TREAS, "MEMBER");

        // 8A. Pending US-15 only -> 400 mentioning US-15
        mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .with(authentication(treasAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalTransactionId": "%s",
                                  "proposedAdjustmentAmount": -1000000.00,
                                  "adjustmentReason": "Đề xuất bù trừ pending"
                                }
                                """.formatted(TX_A_BASE)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("US-15")));

        // 8B. Add Pending US-60 simultaneously -> 400 mentioning BOTH US-15 and US-60
        mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .with(authentication(treasAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Chi quỹ pending",
                                  "category": "Other",
                                  "amount": 5000000.00,
                                  "purpose": "Kiểm thử pending đồng thời"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.allOf(
                        Matchers.containsString("US-15"),
                        Matchers.containsString("US-60")
                )));

        // 8C. On Clan B (clean), Match year 2026, then re-submitting EITHER matched or discrepant for 2026 -> 409 Conflict
        Authentication headB = auth(ACC_B_HEAD, MEM_B_HEAD, "CLAN_HEAD");
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headB)).header(TenantFilter.HEADER_CLAN_ID, CLAN_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 35000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isCreated());

        for (String balance : List.of("35000000.00", "30000000.00")) {
            mockMvc.perform(post("/api/ledger/annual-audit")
                            .with(authentication(headB)).header(TenantFilter.HEADER_CLAN_ID, CLAN_B)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualBalance\": " + balance + ", \"auditYear\": 2026}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    @DisplayName("QA Group 9: Multi-Tenant Isolation, Filter Matrix & SQL Injection Resilience")
    void qaGroup9_multiTenantFilterMatrixAndSqlInjection() throws Exception {
        Authentication headA = auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD");
        Authentication headB = auth(ACC_B_HEAD, MEM_B_HEAD, "CLAN_HEAD");

        String aMatched = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headA)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2025}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String auditAMatchedId = JsonPath.read(aMatched, "$.data.auditId");

        // Clan A audit with SQL injection in notes succeeds and stays intact
        String sqliNotes = "'; DROP TABLE ledger_annual_audits; -- ' OR '1'='1";
        String aDisc = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headA)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 99000000.00, \"auditYear\": 2026, \"notes\": \"" + sqliNotes + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.notes").value(sqliNotes))
                .andReturn().getResponse().getContentAsString();
        String auditADiscId = JsonPath.read(aDisc, "$.data.auditId");

        // Clan B cannot see Clan A's audits or reference TX_A_BASE
        String bMatched = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headB)).header(TenantFilter.HEADER_CLAN_ID, CLAN_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 35000000.00, \"auditYear\": 2025}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String auditBId = JsonPath.read(bMatched, "$.data.auditId");

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headB)).header(TenantFilter.HEADER_CLAN_ID, CLAN_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 35000000.00, \"auditYear\": 2026, \"referenceTransactionId\": \"" + TX_A_BASE + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/ledger/annual-audits/" + auditAMatchedId)
                        .with(authentication(headB)).header(TenantFilter.HEADER_CLAN_ID, CLAN_B))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/ledger/annual-audits/" + auditBId)
                        .with(authentication(headA)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isNotFound());

        // Filter matrix for Clan A
        mockMvc.perform(get("/api/ledger/annual-audits")
                        .with(authentication(headA)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(2)));

        mockMvc.perform(get("/api/ledger/annual-audits").param("status", "Matched")
                        .with(authentication(headA)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[0].auditId").value(auditAMatchedId));

        mockMvc.perform(get("/api/ledger/annual-audits").param("status", "DiscrepancyDetected")
                        .with(authentication(headA)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[0].auditId").value(auditADiscId));

        mockMvc.perform(get("/api/ledger/annual-audits")
                        .param("auditYear", "2026").param("status", "Matched")
                        .with(authentication(headA)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // SQLi on GET by id -> 404
        mockMvc.perform(get("/api/ledger/annual-audits/' OR '1'='1")
                        .with(authentication(headA)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("QA Group 10: PostgreSQL V36 CHECK Constraints, Partial Unique Index & ON DELETE RESTRICT")
    void qaGroup10_postgresqlV36ConstraintsAndForeignKeys() throws Exception {
        Authentication headAuth = auth(ACC_A_HEAD, MEM_A_HEAD, "CLAN_HEAD");

        // Create 1 Matched audit (2025) and 1 Discrepancy audit (2026) -> Reject -> 2nd Discrepancy audit (2026)
        String matchedRes = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2025}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String closingTxId = JsonPath.read(matchedRes, "$.data.closingTransactionId");

        String disc1Res = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 99000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String lar1 = JsonPath.read(disc1Res, "$.data.adjustmentRequestId");

        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\": \"" + lar1 + "\", \"approved\": false, \"rejectionReason\": \"Test lại\"}"))
                .andExpect(status().isOk());

        // Second DiscrepancyDetected row for the same (clan_id, audit_year=2026) IS allowed by partial unique index!
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 98000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("DiscrepancyDetected"));

        entityManager.flush();

        // 10A. Duplicate Matched for (CLAN_A, 2025) -> 23505
        SQLException dupMatchedEx = assertSqlFailureWithSavepoint(
                """
                INSERT INTO ledger_annual_audits (
                    audit_id, clan_id, audit_year, next_period_year,
                    system_balance, actual_balance, discrepancy_amount, audit_status,
                    closing_transaction_id, opening_balance_next_period, auditor_account_id
                ) VALUES ('AUD_QA_DUP', ?, 2025, 2026,
                          100000000.00, 100000000.00, 0.00, 'Matched',
                          ?, 100000000.00, ?)
                """,
                ps -> {
                    ps.setString(1, CLAN_A);
                    ps.setString(2, closingTxId);
                    ps.setString(3, ACC_A_HEAD);
                });
        assertThat(dupMatchedEx.getSQLState()).isEqualTo("23505");

        // 10B. Consistency CHECK violations -> 23514
        SQLException matchedWithNullClosingEx = assertSqlFailureWithSavepoint(
                """
                INSERT INTO ledger_annual_audits (
                    audit_id, clan_id, audit_year, next_period_year,
                    system_balance, actual_balance, discrepancy_amount, audit_status,
                    opening_balance_next_period, auditor_account_id
                ) VALUES ('AUD_QA_BAD1', ?, 2024, 2025,
                          100000000.00, 100000000.00, 0.00, 'Matched',
                          100000000.00, ?)
                """,
                ps -> {
                    ps.setString(1, CLAN_A);
                    ps.setString(2, ACC_A_HEAD);
                });
        assertThat(matchedWithNullClosingEx.getSQLState()).isEqualTo("23514");

        SQLException discWithZeroAmountEx = assertSqlFailureWithSavepoint(
                """
                INSERT INTO ledger_annual_audits (
                    audit_id, clan_id, audit_year, next_period_year,
                    system_balance, actual_balance, discrepancy_amount, audit_status,
                    adjustment_request_id, auditor_account_id
                ) VALUES ('AUD_QA_BAD2', ?, 2024, 2025,
                          100000000.00, 100000000.00, 0.00, 'DiscrepancyDetected',
                          ?, ?)
                """,
                ps -> {
                    ps.setString(1, CLAN_A);
                    ps.setString(2, lar1);
                    ps.setString(3, ACC_A_HEAD);
                });
        assertThat(discWithZeroAmountEx.getSQLState()).isEqualTo("23514");

        SQLException badNextYearEx = assertSqlFailureWithSavepoint(
                """
                INSERT INTO ledger_annual_audits (
                    audit_id, clan_id, audit_year, next_period_year,
                    system_balance, actual_balance, discrepancy_amount, audit_status,
                    closing_transaction_id, opening_balance_next_period, auditor_account_id
                ) VALUES ('AUD_QA_BAD3', ?, 2024, 2030,
                          100000000.00, 100000000.00, 0.00, 'Matched',
                          ?, 100000000.00, ?)
                """,
                ps -> {
                    ps.setString(1, CLAN_A);
                    ps.setString(2, closingTxId);
                    ps.setString(3, ACC_A_HEAD);
                });
        assertThat(badNextYearEx.getSQLState()).isEqualTo("23514");

        // 10C. ON DELETE RESTRICT prevents deleting adjustment request referenced by annual audit
        SQLException fkRestrictEx = assertSqlFailureWithSavepoint(
                "DELETE FROM ledger_adjustment_requests WHERE request_id = ?",
                ps -> ps.setString(1, lar1));
        assertThat(fkRestrictEx.getSQLState()).isIn("23001", "23503");
    }
}
