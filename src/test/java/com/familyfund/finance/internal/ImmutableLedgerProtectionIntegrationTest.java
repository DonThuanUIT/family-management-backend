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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class ImmutableLedgerProtectionIntegrationTest {

    private static final String CLAN_ID = "CLN_US61_IT";
    private static final String BRANCH_ID = "BR_US61_IT";
    private static final String EVENT_ID = "EVT_US61_IT";
    private static final String BASE_TX_ID = "LDG_US61_BASE";
    private static final String HEAD_RAW_PASSWORD = "ClanHead@123";

    private static final String MEMBER_HEAD = "MEM_US61_HEAD";
    private static final String ACCOUNT_HEAD = "ACC_US61_HEAD";

    private static final String MEMBER_TREASURER = "MEM_US61_TREAS";
    private static final String ACCOUNT_TREASURER = "ACC_US61_TREAS";

    private static final String MEMBER_ORDINARY = "MEM_US61_ORD";
    private static final String ACCOUNT_ORDINARY = "ACC_US61_ORD";

    private static final String ACCOUNT_ADMIN = "ACC_US61_ADMIN";

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

    private String baseHash;
    private Instant baseTime;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN_ID);

        clanRepository.save(Clan.builder().clanId(CLAN_ID).clanName("Dòng họ US61 Bất Biến").build());
        FamilyBranch branch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_ID).clanId(CLAN_ID).branchName("Chi Gốc US61").generationLevel(1).build());

        Member head = memberRepository.save(Member.builder()
                .memberId(MEMBER_HEAD).branch(branch).fullName("Cụ Trưởng Tộc US61").gender("Male").generation(1).status("Living").build());
        Member treasurer = memberRepository.save(Member.builder()
                .memberId(MEMBER_TREASURER).branch(branch).fullName("Thủ Quỹ US61").gender("Female").generation(2).status("Living").build());
        Member ordinary = memberRepository.save(Member.builder()
                .memberId(MEMBER_ORDINARY).branch(branch).fullName("Thành Viên US61").gender("Male").generation(3).status("Living").build());

        String encodedPass = passwordEncoder.encode(HEAD_RAW_PASSWORD);
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_HEAD).memberId(head.getMemberId()).phoneNumber("0944000001")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_TREASURER).memberId(treasurer.getMemberId()).phoneNumber("0944000002")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_ORDINARY).memberId(ordinary.getMemberId()).phoneNumber("0944000003")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());

        accountRoleRepository.save(AccountRole.builder()
                .id(new AccountRoleId(ACCOUNT_HEAD, SystemRoles.CLAN_HEAD)).build());

        eventRepository.save(Event.builder()
                .eventId(EVENT_ID).clanId(CLAN_ID).eventName("Đại Lễ Giỗ Tổ US61")
                .eventDatetime(Instant.now().minus(5, ChronoUnit.DAYS)).location("Từ đường")
                .createdBy(ACCOUNT_HEAD).status(EventStatus.Ended).build());
        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVENT_ID).memberId(MEMBER_TREASURER).eventRole(EventRole.EventTreasurer).isReadOnly(false).build());

        baseTime = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        baseHash = ledgerHashCalculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH,
                BASE_TX_ID, CLAN_ID, EVENT_ID, null, null, null,
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"), new BigDecimal("100000000.00"),
                "Kết dư ban đầu US61", ACCOUNT_TREASURER, ACCOUNT_HEAD, baseTime
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(BASE_TX_ID)
                .clanId(CLAN_ID)
                .eventId(EVENT_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00"))
                .runningBalance(new BigDecimal("100000000.00"))
                .description("Kết dư ban đầu US61")
                .createdByAccountId(ACCOUNT_TREASURER)
                .approvedByAccountId(ACCOUNT_HEAD)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH)
                .recordHash(baseHash)
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

    private SQLException assertSqlFailureWithSavepoint(String sql, SqlStatementBinder binder) throws SQLException {
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
    private interface SqlStatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    @Test
    @DisplayName("US-61 Tầng Repository & JPA: Không tồn tại method delete*, cấm ghi đè save() và cấm entityManager.remove()")
    void repositoryAndJpaGuards_blockAllMutationsAndOverwrites() {
        List<String> deleteMethods = Arrays.stream(ImmutableLedgerRepository.class.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("delete"))
                .toList();
        assertThat(deleteMethods).isEmpty();

        // Thử lưu trùng transaction_id để ghi đè -> LedgerImmutabilityViolationException
        ImmutableLedger overwriteAttempt = ImmutableLedger.builder()
                .transactionId(BASE_TX_ID)
                .clanId(CLAN_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("999.00"))
                .runningBalance(new BigDecimal("999.00"))
                .description("Ghi đè bất hợp pháp")
                .createdByAccountId(ACCOUNT_HEAD)
                .approvedByAccountId(ACCOUNT_HEAD)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH)
                .recordHash("a".repeat(64))
                .createdAt(Instant.now())
                .build();

        assertThatThrownBy(() -> immutableLedgerRepository.save(overwriteAttempt))
                .isInstanceOf(LedgerImmutabilityViolationException.class)
                .hasMessageContaining("NFR-01");

        ImmutableLedger managed = immutableLedgerRepository.findById(BASE_TX_ID).orElseThrow();
        assertThatThrownBy(() -> entityManager.remove(managed))
                .isInstanceOf(LedgerImmutabilityViolationException.class)
                .hasMessageContaining("DELETE");
    }

    @Test
    @DisplayName("US-61 Tầng DB Trigger (V34): Chặn tuyệt đối SQL UPDATE, DELETE, UPDATE/DELETE 0 dòng, TRUNCATE và xóa FK cha (events)")
    void databaseTriggers_blockUpdateDeleteTruncateAndParentFkDelete() throws SQLException {
        SQLException updateRowEx = assertSqlFailureWithSavepoint(
                "UPDATE immutable_ledgers SET amount = 0, description = 'Tampered' WHERE transaction_id = ?",
                ps -> ps.setString(1, BASE_TX_ID));
        assertThat(updateRowEx.getSQLState()).isEqualTo("42501");
        assertThat(updateRowEx.getMessage()).contains("SECURITY VIOLATION [NFR-01]");

        SQLException deleteRowEx = assertSqlFailureWithSavepoint(
                "DELETE FROM immutable_ledgers WHERE transaction_id = ?",
                ps -> ps.setString(1, BASE_TX_ID));
        assertThat(deleteRowEx.getSQLState()).isEqualTo("42501");
        assertThat(deleteRowEx.getMessage()).contains("SECURITY VIOLATION [NFR-01]");

        SQLException updateStmtEx = assertSqlFailureWithSavepoint(
                "UPDATE immutable_ledgers SET description = 'Noop' WHERE 1 = 0", null);
        assertThat(updateStmtEx.getSQLState()).isEqualTo("42501");

        SQLException deleteStmtEx = assertSqlFailureWithSavepoint(
                "DELETE FROM immutable_ledgers WHERE 1 = 0", null);
        assertThat(deleteStmtEx.getSQLState()).isEqualTo("42501");

        SQLException truncateEx = assertSqlFailureWithSavepoint(
                "TRUNCATE TABLE immutable_ledgers CASCADE", null);
        assertThat(truncateEx.getSQLState()).isEqualTo("42501");
        assertThat(truncateEx.getMessage()).contains("SECURITY VIOLATION [NFR-01]");
        assertThat(truncateEx.getMessage()).contains("TRUNCATE");

        // ON DELETE RESTRICT ngăn xóa events đã ghi vào immutable_ledgers (SQLSTATE 23001 restrict_violation)
        SQLException deleteEventEx = assertSqlFailureWithSavepoint(
                "DELETE FROM events WHERE event_id = ?",
                ps -> ps.setString(1, EVENT_ID));
        assertThat(deleteEventEx.getSQLState()).isEqualTo("23001");
        assertThat(deleteEventEx.getMessage()).contains("immutable_ledgers");
    }

    @Test
    @DisplayName("US-61 Tầng DB Hash-Chain Trigger (V34): Chặn INSERT gãy previous_hash, lùi timestamp, sai SHA-256 hex; và tự sinh pgcrypto SHA-256 khi raw SQL bỏ trống")
    void databaseHashChainTrigger_guardsIntegrityAndAutoComputesPgCryptoHash() throws SQLException {
        Instant validNextTs = baseTime.plus(1, ChronoUnit.MINUTES);

        // 1. Sai previous_hash -> bị BEFORE INSERT trigger chặn (42501)
        SQLException brokenChainEx = assertSqlFailureWithSavepoint(
                """
                INSERT INTO immutable_ledgers (
                    transaction_id, clan_id, transaction_type, amount, running_balance,
                    description, created_by_account_id, approved_by_account_id,
                    previous_hash, record_hash, created_at
                ) VALUES (?, ?, 'SurplusTransfer', 1000.00, 100001000.00,
                          'Sai prev hash', ?, ?, ?, ?, ?)
                """,
                ps -> {
                    ps.setString(1, "LDG_BAD_PREV");
                    ps.setString(2, CLAN_ID);
                    ps.setString(3, ACCOUNT_TREASURER);
                    ps.setString(4, ACCOUNT_HEAD);
                    ps.setString(5, "0".repeat(64));
                    ps.setString(6, "1".repeat(64));
                    ps.setTimestamp(7, Timestamp.from(validNextTs));
                });
        assertThat(brokenChainEx.getSQLState()).isEqualTo("42501");
        assertThat(brokenChainEx.getMessage()).contains("Broken SHA-256 hash chain");

        // 2. Chèn lùi thời gian (< baseTime) -> bị BEFORE INSERT trigger chặn (42501)
        SQLException backdatedEx = assertSqlFailureWithSavepoint(
                """
                INSERT INTO immutable_ledgers (
                    transaction_id, clan_id, transaction_type, amount, running_balance,
                    description, created_by_account_id, approved_by_account_id,
                    previous_hash, record_hash, created_at
                ) VALUES (?, ?, 'SurplusTransfer', 1000.00, 100001000.00,
                          'Chèn lùi thời gian', ?, ?, ?, ?, ?)
                """,
                ps -> {
                    ps.setString(1, "LDG_BACKDATE");
                    ps.setString(2, CLAN_ID);
                    ps.setString(3, ACCOUNT_TREASURER);
                    ps.setString(4, ACCOUNT_HEAD);
                    ps.setString(5, baseHash);
                    ps.setString(6, "2".repeat(64));
                    ps.setTimestamp(7, Timestamp.from(baseTime.minus(10, ChronoUnit.MINUTES)));
                });
        assertThat(backdatedEx.getSQLState()).isEqualTo("42501");
        assertThat(backdatedEx.getMessage()).contains("Backdated insert rejected");

        // 3. Định dạng record_hash không phải 64 hex -> CHECK constraint chặn (23514)
        SQLException invalidHashFormatEx = assertSqlFailureWithSavepoint(
                """
                INSERT INTO immutable_ledgers (
                    transaction_id, clan_id, transaction_type, amount, running_balance,
                    description, created_by_account_id, approved_by_account_id,
                    previous_hash, record_hash, created_at
                ) VALUES (?, ?, 'SurplusTransfer', 1000.00, 100001000.00,
                          'Sai format hash', ?, ?, ?, 'INVALID_HASH', ?)
                """,
                ps -> {
                    ps.setString(1, "LDG_BAD_FMT");
                    ps.setString(2, CLAN_ID);
                    ps.setString(3, ACCOUNT_TREASURER);
                    ps.setString(4, ACCOUNT_HEAD);
                    ps.setString(5, baseHash);
                    ps.setTimestamp(6, Timestamp.from(validNextTs));
                });
        assertThat(invalidHashFormatEx.getSQLState()).isEqualTo("23514");

        // 4. Raw SQL INSERT bỏ trống previous_hash và record_hash -> PostgreSQL pgcrypto tự nối baseHash và sinh SHA-256 khớp 100% với Java!
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO immutable_ledgers (
                    transaction_id, clan_id, event_id, transaction_type, amount, running_balance,
                    description, created_by_account_id, approved_by_account_id, created_at
                ) VALUES (?, ?, ?, 'SurplusTransfer', 25000000.00, 125000000.00,
                          'Raw SQL INSERT kiểm tra pgcrypto', ?, ?, ?)
                """)) {
            ps.setString(1, "LDG_PGCRYPTO_01");
            ps.setString(2, CLAN_ID);
            ps.setString(3, EVENT_ID);
            ps.setString(4, ACCOUNT_TREASURER);
            ps.setString(5, ACCOUNT_HEAD);
            ps.setTimestamp(6, Timestamp.from(validNextTs));
            ps.executeUpdate();
        }

        entityManager.flush();
        entityManager.clear();

        ImmutableLedger pgCreated = immutableLedgerRepository.findById("LDG_PGCRYPTO_01").orElseThrow();
        assertThat(pgCreated.getPreviousHash()).isEqualTo(baseHash);
        assertThat(pgCreated.getRecordHash()).matches("^[0-9a-f]{64}$");
        assertThat(ledgerHashCalculator.verifyRecordHash(pgCreated, baseHash)).isTrue();
    }

    @Test
    @DisplayName("US-61 Tầng HTTP Security: Cấm toàn bộ HTTP PUT/PATCH/DELETE trên /api/ledger/** và /api/v1/ledger/** với mọi vai trò (kể cả ADMIN/CLAN_HEAD)")
    void httpSecurityFilter_blocksPutPatchDeleteForAllRoles() throws Exception {
        List<Supplier<MockHttpServletRequestBuilder>> mutationRequests = List.of(
                () -> put("/api/ledger/" + BASE_TX_ID),
                () -> patch("/api/ledger/" + BASE_TX_ID),
                () -> delete("/api/ledger/" + BASE_TX_ID),
                () -> put("/api/v1/ledger/" + BASE_TX_ID),
                () -> patch("/api/v1/ledger/adjustments/LAR_001"),
                () -> delete("/api/v1/ledger/disbursements/CFD_001"),
                () -> delete("/api/ledger")
        );

        List<Authentication> callers = List.of(
                auth(ACCOUNT_ADMIN, null, "ADMIN"),
                auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD"),
                auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")
        );

        for (Supplier<MockHttpServletRequestBuilder> reqSupplier : mutationRequests) {
            for (Authentication caller : callers) {
                mockMvc.perform(reqSupplier.get()
                                .with(authentication(caller))
                                .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                        .andExpect(status().isMethodNotAllowed())
                        .andExpect(header().string("Allow", "GET, POST, HEAD, OPTIONS"))
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.code").value(LedgerImmutabilityViolationException.ERROR_CODE));
            }

            // Kể cả request ẩn danh cũng bị chặn 405 với mã vi phạm bất biến
            mockMvc.perform(reqSupplier.get().header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET, POST, HEAD, OPTIONS"))
                    .andExpect(jsonPath("$.code").value(LedgerImmutabilityViolationException.ERROR_CODE));
        }
    }

    @Test
    @DisplayName("US-61 E2E SHA-256 Blockchain Chain: Kết chuyển -> Bù trừ (US-59) -> Chi Quỹ chung (US-60) -> GET /api/v1/ledger/verify-integrity hợp lệ 100%")
    void fullHashChainAcrossUs59AndUs60_verifyIntegrityReturnsValid() throws Exception {
        // 1. Đề xuất & Duyệt bù trừ -5tr (US-59)
        String proposeAdjRes = mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                    "originalTransactionId": "%s",
                                    "proposedAdjustmentAmount": -5000000.00,
                                    "adjustmentReason": "Giảm trừ chi phí sự kiện"
                                }
                                """.formatted(BASE_TX_ID)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String adjReqId = com.jayway.jsonpath.JsonPath.read(proposeAdjRes, "$.data.requestId");

        mockMvc.perform(post("/api/ledger/review-adjustment")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                    "requestId": "%s",
                                    "approved": true
                                }
                                """.formatted(adjReqId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerEntry.previousHash").value(baseHash))
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(95000000.00));

        // 2. Đề xuất & Duyệt chi Quỹ chung 20tr (US-60)
        String proposeDisbRes = mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                    "title": "Tu sửa bia đá Từ đường",
                                    "category": "TempleMaintenance",
                                    "amount": 20000000.00,
                                    "purpose": "Khắc phục bia đá bị mờ chữ"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String disbReqId = com.jayway.jsonpath.JsonPath.read(proposeDisbRes, "$.data.requestId");

        String approveDisbRes = mockMvc.perform(post("/api/v1/ledger/disbursements/" + disbReqId + "/review")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                    "approved": true,
                                    "confirmPassword": "%s"
                                }
                                """.formatted(HEAD_RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(75000000.00))
                .andReturn().getResponse().getContentAsString();
        String latestTxId = com.jayway.jsonpath.JsonPath.read(approveDisbRes, "$.data.ledgerEntry.transactionId");
        String latestHash = com.jayway.jsonpath.JsonPath.read(approveDisbRes, "$.data.ledgerEntry.recordHash");

        entityManager.flush();
        entityManager.clear();

        // 3. Kiểm toán chuỗi khối SHA-256 qua API công khai thành viên dòng họ
        mockMvc.perform(get("/api/v1/ledger/verify-integrity")
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.clanId").value(CLAN_ID))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.totalEntries").value(3))
                .andExpect(jsonPath("$.data.verifiedEntries").value(3))
                .andExpect(jsonPath("$.data.genesisAnchor").value("GENESIS"))
                .andExpect(jsonPath("$.data.latestTransactionId").value(latestTxId))
                .andExpect(jsonPath("$.data.latestRecordHash").value(latestHash))
                .andExpect(jsonPath("$.data.brokenLinks").isEmpty());
    }
}
