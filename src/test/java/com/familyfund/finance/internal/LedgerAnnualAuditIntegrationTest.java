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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Instant;
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
class LedgerAnnualAuditIntegrationTest {

    private static final String CLAN_ID = "CLN_AUD_IT";
    private static final String CLAN_OTHER = "CLN_AUD_OTH";
    private static final String BRANCH_ID = "BR_AUD_IT";
    private static final String BRANCH_OTHER = "BR_AUD_OTH";
    private static final String EVENT_ID = "EVT_AUD_IT";
    private static final String BASE_TX_ID = "LDG_AUD_BASE";
    private static final String FOREIGN_TX_ID = "LDG_AUD_FRN";
    private static final String HEAD_RAW_PASSWORD = "ClanHeadAud@123";

    private static final String MEMBER_HEAD = "MEM_AUD_HEAD";
    private static final String ACCOUNT_HEAD = "ACC_AUD_HEAD";

    private static final String MEMBER_TREASURER = "MEM_AUD_TREAS";
    private static final String ACCOUNT_TREASURER = "ACC_AUD_TREAS";

    private static final String MEMBER_ORDINARY = "MEM_AUD_ORD";
    private static final String ACCOUNT_ORDINARY = "ACC_AUD_ORD";

    private static final String MEMBER_OTHER = "MEM_AUD_OTH";
    private static final String ACCOUNT_OTHER = "ACC_AUD_OTH";

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
        TenantContext.setClanId(CLAN_ID);

        clanRepository.save(Clan.builder().clanId(CLAN_ID).clanName("Dòng họ Kiểm Toán").build());
        clanRepository.save(Clan.builder().clanId(CLAN_OTHER).clanName("Dòng họ Ngoại Lai").build());

        FamilyBranch branch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_ID).clanId(CLAN_ID).branchName("Chi Gốc Kiểm Toán").generationLevel(1).build());
        FamilyBranch branchOther = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_OTHER).clanId(CLAN_OTHER).branchName("Chi Ngoại Lai").generationLevel(1).build());

        Member head = memberRepository.save(Member.builder()
                .memberId(MEMBER_HEAD).branch(branch).fullName("Trưởng Tộc Kiểm Toán").gender("Male").generation(1).status("Living").build());
        Member treasurer = memberRepository.save(Member.builder()
                .memberId(MEMBER_TREASURER).branch(branch).fullName("Thủ Quỹ Kiểm Toán").gender("Female").generation(2).status("Living").build());
        Member ordinary = memberRepository.save(Member.builder()
                .memberId(MEMBER_ORDINARY).branch(branch).fullName("Thành Viên Kiểm Toán").gender("Male").generation(3).status("Living").build());
        Member otherMember = memberRepository.save(Member.builder()
                .memberId(MEMBER_OTHER).branch(branchOther).fullName("Người Họ Khác").gender("Male").generation(2).status("Living").build());

        String encodedPass = passwordEncoder.encode(HEAD_RAW_PASSWORD);
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_HEAD).memberId(head.getMemberId()).phoneNumber("0977000001")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_TREASURER).memberId(treasurer.getMemberId()).phoneNumber("0977000002")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_ORDINARY).memberId(ordinary.getMemberId()).phoneNumber("0977000003")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_OTHER).memberId(otherMember.getMemberId()).phoneNumber("0977000004")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());

        accountRoleRepository.save(AccountRole.builder()
                .id(new AccountRoleId(ACCOUNT_HEAD, SystemRoles.CLAN_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder()
                .id(new AccountRoleId(ACCOUNT_OTHER, SystemRoles.CLAN_HEAD)).build());

        eventRepository.save(Event.builder()
                .eventId(EVENT_ID).clanId(CLAN_ID).eventName("Đại Lễ Giỗ Tổ 2026")
                .eventDatetime(Instant.now().minus(5, ChronoUnit.DAYS)).location("Từ đường")
                .createdBy(ACCOUNT_HEAD).status(EventStatus.Ended).build());
        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVENT_ID).memberId(MEMBER_TREASURER).eventRole(EventRole.EventTreasurer).isReadOnly(false).build());

        baseTime = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        String baseHash = ledgerHashCalculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH,
                BASE_TX_ID, CLAN_ID, EVENT_ID, null, null, null,
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"), new BigDecimal("100000000.00"),
                "Kết dư Đại Lễ Giỗ Tổ 2026", ACCOUNT_TREASURER, ACCOUNT_HEAD, baseTime
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(BASE_TX_ID)
                .clanId(CLAN_ID)
                .eventId(EVENT_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00"))
                .runningBalance(new BigDecimal("100000000.00"))
                .description("Kết dư Đại Lễ Giỗ Tổ 2026")
                .createdByAccountId(ACCOUNT_TREASURER)
                .approvedByAccountId(ACCOUNT_HEAD)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH)
                .recordHash(baseHash)
                .createdAt(baseTime)
                .build());

        String foreignHash = ledgerHashCalculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH,
                FOREIGN_TX_ID, CLAN_OTHER, null, null, null, null,
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("20000000.00"), new BigDecimal("20000000.00"),
                "Kết dư Họ Khác", ACCOUNT_OTHER, ACCOUNT_OTHER, baseTime
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(FOREIGN_TX_ID)
                .clanId(CLAN_OTHER)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("20000000.00"))
                .runningBalance(new BigDecimal("20000000.00"))
                .description("Kết dư Họ Khác")
                .createdByAccountId(ACCOUNT_OTHER)
                .approvedByAccountId(ACCOUNT_OTHER)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH)
                .recordHash(foreignHash)
                .createdAt(baseTime)
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
    @DisplayName("E2E: Kiểm toán lệch thiếu (-5tr) -> tự sinh US-15 -> chặn khi Pending -> duyệt US-59 -> kiểm toán lại khớp -> chốt OpeningBalanceCarryForward + US-61/US-62 + chặn 409")
    void fullE2eFlow_deficitAutoAdjustment_approve_matchedRollover_andGuards() throws Exception {
        // 1. Lần 1: thực kiểm 95,000,000 vs sổ cái 100,000,000 -> tự động sinh đề xuất US-15 (-5,000,000)
        String discResponse = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualBalance": 95000000.00,
                                  "auditYear": 2026,
                                  "notes": "Kiểm quỹ cuối năm phát hiện thiếu chứng từ 5 triệu",
                                  "evidenceAttachmentUrl": "https://cdn.familyfund.vn/audit-2026-disc.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auditStatus").value("DiscrepancyDetected"))
                .andExpect(jsonPath("$.data.systemBalance").value(100000000.00))
                .andExpect(jsonPath("$.data.actualBalance").value(95000000.00))
                .andExpect(jsonPath("$.data.discrepancyAmount").value(-5000000.00))
                .andExpect(jsonPath("$.data.auditorDisplayName").value("Trưởng Tộc Kiểm Toán"))
                .andExpect(jsonPath("$.data.closingTransactionId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.openingBalanceNextPeriod").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.autoGeneratedAdjustmentRequest.originalTransactionId").value(BASE_TX_ID))
                .andExpect(jsonPath("$.data.autoGeneratedAdjustmentRequest.proposedAdjustmentAmount").value(-5000000.00))
                .andExpect(jsonPath("$.data.autoGeneratedAdjustmentRequest.approvalStatus").value("Pending"))
                .andReturn().getResponse().getContentAsString();

        String discAuditId = JsonPath.read(discResponse, "$.data.auditId");
        String adjRequestId = JsonPath.read(discResponse, "$.data.autoGeneratedAdjustmentRequest.requestId");

        // 2. Gọi lại kiểm toán trong khi đề xuất US-15 vẫn Pending -> 400 Bad Request
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualBalance": 95000000.00,
                                  "auditYear": 2026
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("US-15")));

        // 3. Trưởng tộc duyệt đề xuất bù trừ qua US-59 -> runningBalance giảm về 95,000,000
        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "%s",
                                  "approved": true
                                }
                                """.formatted(adjRequestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(95000000.00));

        // 4. Lần 2: kiểm toán lại cùng năm 2026 qua /api/v1/ledger/annual-audit -> Khớp -> chốt OpeningBalanceCarryForward
        String matchedResponse = mockMvc.perform(post("/api/v1/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualBalance": 95000000.00,
                                  "auditYear": 2026,
                                  "notes": "Đối khớp hoàn tất sau bù trừ"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auditStatus").value("Matched"))
                .andExpect(jsonPath("$.data.auditYear").value(2026))
                .andExpect(jsonPath("$.data.nextPeriodYear").value(2027))
                .andExpect(jsonPath("$.data.discrepancyAmount").value(0.00))
                .andExpect(jsonPath("$.data.openingBalanceNextPeriod").value(95000000.00))
                .andExpect(jsonPath("$.data.closingLedgerEntry.transactionType").value("OpeningBalanceCarryForward"))
                .andExpect(jsonPath("$.data.closingLedgerEntry.amount").value(0.00))
                .andExpect(jsonPath("$.data.closingLedgerEntry.runningBalance").value(95000000.00))
                .andExpect(jsonPath("$.data.closingLedgerEntry.originalTransactionId").value(Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();

        String matchedAuditId = JsonPath.read(matchedResponse, "$.data.auditId");
        String closingTxId = JsonPath.read(matchedResponse, "$.data.closingLedgerEntry.transactionId");

        entityManager.flush();
        entityManager.clear();

        // 5. Chuỗi SHA-256 (US-61) vẫn toàn vẹn 100% qua cả 3 mắt xích (SurplusTransfer -> Adjustment -> OpeningBalanceCarryForward)
        mockMvc.perform(get("/api/v1/ledger/verify-integrity")
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalEntries").value(3))
                .andExpect(jsonPath("$.data.verifiedEntries").value(3))
                .andExpect(jsonPath("$.data.latestTransactionId").value(closingTxId))
                .andExpect(jsonPath("$.data.brokenLinks").isEmpty());

        // 6. Tra cứu lịch sử Sổ cái (US-62): tổng thu/chi không bị méo do bút toán chốt sổ 0.00 và không nhận nhầm là bù trừ
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.currentFundBalance").value(95000000.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalInflow").value(100000000.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalOutflow").value(5000000.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(95000000.00));

        mockMvc.perform(get("/api/ledger")
                        .param("transactionType", "OpeningBalanceCarryForward")
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(closingTxId))
                .andExpect(jsonPath("$.data.items[0].isAdjustment").value(false))
                .andExpect(jsonPath("$.data.items[0].hasAdjustments").value(false));

        // 7. Tra cứu lịch sử kiểm toán GET /api/ledger/annual-audits & GET /api/v1/ledger/annual-audits/{auditId}
        mockMvc.perform(get("/api/ledger/annual-audits")
                        .param("auditYear", "2026")
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.data[0].auditId").value(matchedAuditId))
                .andExpect(jsonPath("$.data[0].closingLedgerEntry.transactionId").value(closingTxId))
                .andExpect(jsonPath("$.data[1].auditId").value(discAuditId))
                .andExpect(jsonPath("$.data[1].autoGeneratedAdjustmentRequest.approvalStatus").value("Approved"));

        mockMvc.perform(get("/api/v1/ledger/annual-audits/" + matchedAuditId)
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditId").value(matchedAuditId));

        // 8. Đã chốt Matched năm 2026 -> gọi lại bị chặn 409 Conflict
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualBalance": 95000000.00,
                                  "auditYear": 2026
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("E2E: Kiểm toán lệch thừa (+3tr) -> tự sinh bù trừ dương -> duyệt US-59 -> chốt khớp 103tr")
    void fullE2eFlow_surplusAutoAdjustment_thenApproveAndMatch() throws Exception {
        String discResponse = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actual_balance": 103000000.00,
                                  "audit_year": 2025,
                                  "reference_transaction_id": "%s",
                                  "audit_notes": "Thừa tiền quỹ 3 triệu do khách đóng bổ sung"
                                }
                                """.formatted(BASE_TX_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("DiscrepancyDetected"))
                .andExpect(jsonPath("$.data.discrepancyAmount").value(3000000.00))
                .andExpect(jsonPath("$.data.autoGeneratedAdjustmentRequest.proposedAdjustmentAmount").value(3000000.00))
                .andReturn().getResponse().getContentAsString();

        String adjRequestId = JsonPath.read(discResponse, "$.data.autoGeneratedAdjustmentRequest.requestId");

        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "approved": true}
                                """.formatted(adjRequestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(103000000.00));

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualBalance": 103000000.00, "auditYear": 2025}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.auditStatus").value("Matched"))
                .andExpect(jsonPath("$.data.openingBalanceNextPeriod").value(103000000.00));
    }

    @Test
    @DisplayName("Chặn kiểm toán khi còn đề xuất chi Quỹ chung (US-60) đang Pending; chặn RBAC, Cross-Tenant & HTTP PUT/PATCH/DELETE")
    void guards_pendingDisbursement_rbac_crossTenant_httpImmutability_andDbConstraints() throws Exception {
        // 0. Cross-tenant: clan chính không được truyền referenceTransactionId thuộc clan khác -> 404
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualBalance": 99000000.00,
                                  "auditYear": 2026,
                                  "referenceTransactionId": "%s"
                                }
                                """.formatted(FOREIGN_TX_ID)))
                .andExpect(status().isNotFound());

        // 1. Tạo đề xuất chi Quỹ chung US-60 trạng thái Pending -> kiểm toán bị chặn 400
        mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Tu sửa mái ngói Từ đường",
                                  "category": "TempleMaintenance",
                                  "amount": 10000000.00,
                                  "purpose": "Mua ngói âm dương"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("US-60")));

        // 2. RBAC: MEMBER và BRANCH_HEAD không được POST /annual-audit (403)
        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "BRANCH_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 100000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isForbidden());

        // 3. Cross-tenant: clan chính không được tham chiếu referenceTransactionId của clan khác (404)
        //    Và clan khác không đọc được biên bản kiểm toán của clan chính (404)
        String otherClanAudit = mockMvc.perform(post("/api/ledger/annual-audit")
                        .with(authentication(auth(ACCOUNT_OTHER, MEMBER_OTHER, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_OTHER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualBalance\": 20000000.00, \"auditYear\": 2026}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String otherAuditId = JsonPath.read(otherClanAudit, "$.data.auditId");

        mockMvc.perform(get("/api/ledger/annual-audits/" + otherAuditId)
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isNotFound());

        // 4. HTTP immutability guard (US-61): PUT/PATCH/DELETE bị chặn 405
        List<Supplier<MockHttpServletRequestBuilder>> forbiddenCalls = List.of(
                () -> put("/api/ledger/annual-audit"),
                () -> patch("/api/ledger/annual-audit"),
                () -> delete("/api/ledger/annual-audit"),
                () -> delete("/api/v1/ledger/annual-audits/" + otherAuditId)
        );
        for (Supplier<MockHttpServletRequestBuilder> req : forbiddenCalls) {
            mockMvc.perform(req.get()
                            .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET, POST, HEAD, OPTIONS"))
                    .andExpect(jsonPath("$.code").value(LedgerImmutabilityViolationException.ERROR_CODE));
        }

        // 5. DB constraint checks (V36): Partial Unique Index uq_annual_audit_clan_year_matched & CHECK consistency
        entityManager.flush();
        String closingTxOther = JsonPath.read(otherClanAudit, "$.data.closingTransactionId");

        SQLException dupMatchedEx = assertSqlFailureWithSavepoint(
                """
                INSERT INTO ledger_annual_audits (
                    audit_id, clan_id, audit_year, next_period_year,
                    system_balance, actual_balance, discrepancy_amount, audit_status,
                    closing_transaction_id, opening_balance_next_period, auditor_account_id
                ) VALUES ('AUD_DUP_2026', ?, 2026, 2027,
                          20000000.00, 20000000.00, 0.00, 'Matched',
                          ?, 20000000.00, ?)
                """,
                ps -> {
                    ps.setString(1, CLAN_OTHER);
                    ps.setString(2, closingTxOther);
                    ps.setString(3, ACCOUNT_OTHER);
                });
        assertThat(dupMatchedEx.getSQLState()).isEqualTo("23505");

        SQLException badCheckEx = assertSqlFailureWithSavepoint(
                """
                INSERT INTO ledger_annual_audits (
                    audit_id, clan_id, audit_year, next_period_year,
                    system_balance, actual_balance, discrepancy_amount, audit_status,
                    closing_transaction_id, opening_balance_next_period, auditor_account_id
                ) VALUES ('AUD_BAD_CHK', ?, 2024, 2025,
                          20000000.00, 19000000.00, -1000000.00, 'Matched',
                          ?, 19000000.00, ?)
                """,
                ps -> {
                    ps.setString(1, CLAN_OTHER);
                    ps.setString(2, closingTxOther);
                    ps.setString(3, ACCOUNT_OTHER);
                });
        assertThat(badCheckEx.getSQLState()).isEqualTo("23514");
    }
}
