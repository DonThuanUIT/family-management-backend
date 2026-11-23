package com.familyfund.finance.internal;

import com.familyfund.core.exception.LedgerImmutabilityViolationException;
import com.familyfund.core.security.MemberLinkedPrincipal;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.tenant.TenantFilter;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.entity.CommonFundDisbursementRequest;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.entity.LedgerAdjustmentRequest;
import com.familyfund.finance.internal.repository.CommonFundDisbursementRequestRepository;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.repository.LedgerAdjustmentRequestRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive adversarial QA integration suite for US-62 Ledger History APIs
 * ({@code GET /api/ledger}, {@code GET /api/v1/ledger}, {@code GET /api/ledger/immutable},
 * {@code GET /api/ledger/transactions/{transactionId}}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImmutableLedgerQueryQaIntegrationTest {

    private static final String CLAN_ID = "CLNQ62MAIN";
    private static final String CLAN_EMPTY = "CLNQ62EMPTY";
    private static final String CLAN_OTHER = "CLNQ62OTHER";

    private static final String BRANCH_ID = "BRQ62MAIN";
    private static final String BRANCH_OTHER = "BRQ62OTHER";

    private static final String EVT_A = "EVTQ62A";
    private static final String EVT_B = "EVTQ62B";

    private static final String TX1 = "LDGQ62T01";
    private static final String TX2 = "LDGQ62T02";
    private static final String TX3 = "LDGQ62T03";
    private static final String TX4 = "LDGQ62T04";
    private static final String TX5 = "LDGQ62T05";
    private static final String TX6 = "LDGQ62T06";
    private static final String TX7 = "LDGQ62T07";
    private static final String TX8 = "LDGQ62T08";

    private static final String MEM_HEAD = "MEMQ62HEAD";
    private static final String ACC_HEAD = "ACCQ62HEAD";
    private static final String MEM_TR = "MEMQ62TR";
    private static final String ACC_TR = "ACCQ62TR";
    private static final String MEM_BR = "MEMQ62BR";
    private static final String ACC_BR = "ACCQ62BR";
    private static final String MEM_ORD = "MEMQ62ORD";
    private static final String ACC_ORD = "ACCQ62ORD";
    private static final String ACC_ADMIN = "ACCQ62ADMIN";
    private static final String ACC_OTHER = "ACCQ62OTHER";
    private static final String MEM_OTHER = "MEMQ62OTHER";

    // Unlinked accounts testing IdentityRoleCommandService.findAccountDisplayNames fallback chain
    private static final String ACC_PHONE_ONLY = "ACCQ62PHONE";
    private static final String ACC_EMAIL_ONLY = "ACCQ62EMAIL";
    private static final String ACC_BLANK_ALL = "ACCQ62BLANK";

    private static final Instant T1_OCT10_END_VN = Instant.parse("2026-10-10T16:59:59Z"); // 2026-10-10 23:59:59+07:00
    private static final Instant T2_OCT11_START_VN = Instant.parse("2026-10-10T17:00:00Z"); // 2026-10-11 00:00:00+07:00
    private static final Instant T3_OCT12 = Instant.parse("2026-10-12T03:00:00Z");
    private static final Instant T4_OCT13 = Instant.parse("2026-10-13T04:00:00Z");
    private static final Instant T5_OCT14 = Instant.parse("2026-10-14T05:00:00Z");
    private static final Instant T6_OCT15 = Instant.parse("2026-10-15T06:00:00Z");
    private static final Instant T7_OCT16 = Instant.parse("2026-10-16T07:00:00Z");
    private static final Instant T8_OCT17 = Instant.parse("2026-10-17T08:00:00Z");

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
    private LedgerAdjustmentRequestRepository adjustmentRequestRepository;

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
    void seedQaLedger() {
        TenantContext.setClanId(CLAN_ID);

        clanRepository.save(Clan.builder().clanId(CLAN_ID).clanName("Dòng họ QA US62").build());
        clanRepository.save(Clan.builder().clanId(CLAN_EMPTY).clanName("Dòng họ QA Trống").build());
        clanRepository.save(Clan.builder().clanId(CLAN_OTHER).clanName("Dòng họ QA Khác").build());

        FamilyBranch branch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_ID).clanId(CLAN_ID).branchName("Chi Gốc Q62").generationLevel(1).build());
        FamilyBranch otherBranch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_OTHER).clanId(CLAN_OTHER).branchName("Chi Khác Q62").generationLevel(1).build());

        Member mHead = memberRepository.save(Member.builder()
                .memberId(MEM_HEAD).branch(branch).fullName("Cụ Trưởng Tộc Q62").gender("Male").generation(1).status("Living").build());
        Member mTr = memberRepository.save(Member.builder()
                .memberId(MEM_TR).branch(branch).fullName("Thủ Quỹ Q62").gender("Female").generation(2).status("Living").build());
        Member mBr = memberRepository.save(Member.builder()
                .memberId(MEM_BR).branch(branch).fullName("Trưởng Chi Q62").gender("Male").generation(2).status("Living").build());
        Member mOrd = memberRepository.save(Member.builder()
                .memberId(MEM_ORD).branch(branch).fullName("Thành Viên Q62").gender("Male").generation(3).status("Living").build());
        Member mOth = memberRepository.save(Member.builder()
                .memberId(MEM_OTHER).branch(otherBranch).fullName("Người Họ Khác Q62").gender("Male").generation(2).status("Living").build());

        String pw = passwordEncoder.encode("QaUs62@2026!");
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_HEAD).memberId(mHead.getMemberId()).phoneNumber("0971000001")
                .passwordHash(pw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_TR).memberId(mTr.getMemberId()).phoneNumber("0971000002")
                .passwordHash(pw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_BR).memberId(mBr.getMemberId()).phoneNumber("0971000003")
                .passwordHash(pw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_ORD).memberId(mOrd.getMemberId()).phoneNumber("0971000004")
                .passwordHash(pw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_ADMIN).memberId(null).phoneNumber("0971000005")
                .passwordHash(pw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_OTHER).memberId(mOth.getMemberId()).phoneNumber("0971000006")
                .passwordHash(pw).accountStatus(AccountStatus.Active).build());

        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_PHONE_ONLY).memberId(null).phoneNumber("0999888777")
                .email(null).passwordHash(pw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_EMAIL_ONLY).memberId(null).phoneNumber("   ")
                .email("emailonly@qa.vn").passwordHash(pw).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACC_BLANK_ALL).memberId(null).phoneNumber("    ")
                .email("  ").passwordHash(pw).accountStatus(AccountStatus.Active).build());

        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_HEAD, SystemRoles.CLAN_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_BR, SystemRoles.BRANCH_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder().id(new AccountRoleId(ACC_ADMIN, SystemRoles.ADMIN)).build());

        eventRepository.save(Event.builder()
                .eventId(EVT_A).clanId(CLAN_ID).eventName("Đại Lễ Giỗ Tổ Q62")
                .eventDatetime(Instant.parse("2026-10-09T02:00:00Z")).location("Từ đường A")
                .createdBy(ACC_HEAD).status(EventStatus.Closed).build());
        eventRepository.save(Event.builder()
                .eventId(EVT_B).clanId(CLAN_ID).eventName("Lễ Chạp Mả Q62")
                .eventDatetime(Instant.parse("2026-10-08T02:00:00Z")).location("Từ đường B")
                .createdBy(ACC_HEAD).status(EventStatus.Closed).build());

        // Seed 8-entry hash chain in CLAN_ID with strict chronological order
        String prev = ImmutableLedgerHashCalculator.GENESIS_HASH;

        prev = saveLedger(prev, TX1, CLAN_ID, EVT_B, null, null, null,
                LedgerTransactionType.SurplusTransfer, "50000000.00", "50000000.00",
                "Kết dư 100% hoàn chỉnh lễ B", ACC_PHONE_ONLY, ACC_EMAIL_ONLY, T1_OCT10_END_VN);

        prev = saveLedger(prev, TX2, CLAN_ID, EVT_A, null, null, null,
                LedgerTransactionType.SurplusTransfer, "100000000.00", "150000000.00",
                "Kết dư Đại Lễ Giỗ Tổ 100000000 đồng", ACC_TR, ACC_HEAD, T2_OCT11_START_VN);

        adjustmentRequestRepository.saveAndFlush(LedgerAdjustmentRequest.builder()
                .requestId("LARQ6201").originalTransactionId(TX2)
                .proposedAdjustmentAmount(new BigDecimal("-5000000.00"))
                .adjustmentReason("Giảm trừ hóa đơn âm thanh").evidenceAttachmentUrl("https://cdn.qa.vn/r1.pdf")
                .proposedByAccountId(ACC_TR).approvalStatus(LedgerAdjustmentStatus.Approved)
                .reviewedByAccountId(ACC_HEAD).createdAt(T3_OCT12.minusSeconds(60)).reviewedAt(T3_OCT12).build());

        prev = saveLedger(prev, TX3, CLAN_ID, EVT_A, "LARQ6201", null, TX2,
                LedgerTransactionType.AdjustmentTransaction, "-5000000.00", "145000000.00",
                "Bù trừ giảm hóa đơn âm thanh", ACC_TR, ACC_HEAD, T3_OCT12);

        adjustmentRequestRepository.saveAndFlush(LedgerAdjustmentRequest.builder()
                .requestId("LARQ6202").originalTransactionId(TX2)
                .proposedAdjustmentAmount(new BigDecimal("2000000.00"))
                .adjustmentReason("Bổ sung tiền mừng thu muộn").evidenceAttachmentUrl("https://cdn.qa.vn/r2.pdf")
                .proposedByAccountId(ACC_TR).approvalStatus(LedgerAdjustmentStatus.Approved)
                .reviewedByAccountId(ACC_HEAD).createdAt(T4_OCT13.minusSeconds(60)).reviewedAt(T4_OCT13).build());

        prev = saveLedger(prev, TX4, CLAN_ID, EVT_A, "LARQ6202", null, TX2,
                LedgerTransactionType.AdjustmentTransaction, "2000000.00", "147000000.00",
                "Bù trừ bổ sung thu lễ vật", ACC_TR, ACC_HEAD, T4_OCT13);

        // 1 Pending and 1 Rejected request on TX2 (Pending counts = 1; Rejected does NOT count)
        adjustmentRequestRepository.saveAndFlush(LedgerAdjustmentRequest.builder()
                .requestId("LARQ62P1").originalTransactionId(TX2)
                .proposedAdjustmentAmount(new BigDecimal("-1000000.00"))
                .adjustmentReason("Chờ Trưởng tộc duyệt").proposedByAccountId(ACC_TR)
                .approvalStatus(LedgerAdjustmentStatus.Pending).createdAt(T4_OCT13.plusSeconds(30)).build());
        adjustmentRequestRepository.saveAndFlush(LedgerAdjustmentRequest.builder()
                .requestId("LARQ62R1").originalTransactionId(TX2)
                .proposedAdjustmentAmount(new BigDecimal("-3000000.00"))
                .adjustmentReason("Đề xuất sai bị từ chối").proposedByAccountId(ACC_TR)
                .approvalStatus(LedgerAdjustmentStatus.Rejected).reviewedByAccountId(ACC_HEAD)
                .rejectionReason("Không hợp lệ").createdAt(T4_OCT13.plusSeconds(60))
                .reviewedAt(T4_OCT13.plusSeconds(90)).build());

        // Nested adjustment TX5 -> TX3
        adjustmentRequestRepository.saveAndFlush(LedgerAdjustmentRequest.builder()
                .requestId("LARQ6203").originalTransactionId(TX3)
                .proposedAdjustmentAmount(new BigDecimal("1000000.00"))
                .adjustmentReason("Đính chính lồng cấp trên LDGQ62T03").proposedByAccountId(ACC_BLANK_ALL)
                .approvalStatus(LedgerAdjustmentStatus.Approved).reviewedByAccountId(ACC_HEAD)
                .createdAt(T5_OCT14.minusSeconds(60)).reviewedAt(T5_OCT14).build());

        prev = saveLedger(prev, TX5, CLAN_ID, EVT_A, "LARQ6203", null, TX3,
                LedgerTransactionType.AdjustmentTransaction, "1000000.00", "148000000.00",
                "Đính chính lồng cấp trên bút toán LDGQ62T03", ACC_BLANK_ALL, ACC_HEAD, T5_OCT14);

        // Disbursement TX6 + Adjustment TX7 on TX6
        disbursementRepository.saveAndFlush(CommonFundDisbursementRequest.builder()
                .requestId("CFDQ6201").clanId(CLAN_ID).title("Tu sửa mái Từ đường QA")
                .category(CommonFundExpenseCategory.TempleMaintenance).amount(new BigDecimal("20000000.00"))
                .purpose("Sửa ngói").proposedByAccountId(ACC_TR)
                .approvalStatus(CommonFundDisbursementStatus.Approved).reviewedByAccountId(ACC_HEAD)
                .createdAt(T6_OCT15.minusSeconds(60)).reviewedAt(T6_OCT15).build());

        prev = saveLedger(prev, TX6, CLAN_ID, null, null, "CFDQ6201", null,
                LedgerTransactionType.CommonFundDisbursement, "-20000000.00", "128000000.00",
                "Chi tu sửa mái Từ đường", ACC_TR, ACC_HEAD, T6_OCT15);

        adjustmentRequestRepository.saveAndFlush(LedgerAdjustmentRequest.builder()
                .requestId("LARQ6204").originalTransactionId(TX6)
                .proposedAdjustmentAmount(new BigDecimal("3000000.00"))
                .adjustmentReason("Hoàn nhập vật tư thừa").proposedByAccountId(ACC_TR)
                .approvalStatus(LedgerAdjustmentStatus.Approved).reviewedByAccountId(ACC_HEAD)
                .createdAt(T7_OCT16.minusSeconds(60)).reviewedAt(T7_OCT16).build());

        prev = saveLedger(prev, TX7, CLAN_ID, null, "LARQ6204", null, TX6,
                LedgerTransactionType.AdjustmentTransaction, "3000000.00", "131000000.00",
                "Hoàn nhập chi dư tu sửa mái Từ đường", ACC_TR, ACC_HEAD, T7_OCT16);

        // Unadjusted Disbursement TX8
        disbursementRepository.saveAndFlush(CommonFundDisbursementRequest.builder()
                .requestId("CFDQ6202").clanId(CLAN_ID).title("Cứu trợ bão lụt QA")
                .category(CommonFundExpenseCategory.ReliefSupport).amount(new BigDecimal("11000000.00"))
                .purpose("Hỗ trợ hộ khó khăn").proposedByAccountId(ACC_TR)
                .approvalStatus(CommonFundDisbursementStatus.Approved).reviewedByAccountId(ACC_HEAD)
                .createdAt(T8_OCT17.minusSeconds(60)).reviewedAt(T8_OCT17).build());

        saveLedger(prev, TX8, CLAN_ID, null, null, "CFDQ6202", null,
                LedgerTransactionType.CommonFundDisbursement, "-11000000.00", "120000000.00",
                "Chi cứu trợ con cháu vùng lũ", ACC_TR, ACC_HEAD, T8_OCT17);

        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private String saveLedger(
            String prevHash,
            String txId,
            String clanId,
            String eventId,
            String adjReqId,
            String disbReqId,
            String origTxId,
            LedgerTransactionType type,
            String amountStr,
            String balanceStr,
            String desc,
            String createdBy,
            String approvedBy,
            Instant createdAt
    ) {
        BigDecimal amount = new BigDecimal(amountStr);
        BigDecimal runningBalance = new BigDecimal(balanceStr);
        String hash = ledgerHashCalculator.computeRecordHash(
                prevHash, txId, clanId, eventId, adjReqId, disbReqId, origTxId,
                type, amount, runningBalance, desc, createdBy, approvedBy, createdAt
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(txId)
                .clanId(clanId)
                .eventId(eventId)
                .adjustmentRequestId(adjReqId)
                .disbursementRequestId(disbReqId)
                .originalTransactionId(origTxId)
                .transactionType(type)
                .amount(amount)
                .runningBalance(runningBalance)
                .description(desc)
                .createdByAccountId(createdBy)
                .approvedByAccountId(approvedBy)
                .previousHash(prevHash)
                .recordHash(hash)
                .createdAt(createdAt)
                .build());
        return hash;
    }

    private Authentication auth(String accountId, String memberId, String... roles) {
        List<SimpleGrantedAuthority> auths = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new UsernamePasswordAuthenticationToken(new Principal(accountId, memberId), "N/A", auths);
    }

    @Test
    @DisplayName("QA Group 1: Auth, RBAC, Empty Clan & Cross-Tenant Isolation")
    void qaGroup1_authRbacEmptyClanAndCrossTenant() throws Exception {
        // 1A. Unauthenticated -> 401
        for (Supplier<MockHttpServletRequestBuilder> unauth : List.<Supplier<MockHttpServletRequestBuilder>>of(
                () -> get("/api/ledger"),
                () -> get("/api/v1/ledger"),
                () -> get("/api/ledger/immutable"),
                () -> get("/api/ledger/transactions/" + TX1)
        )) {
            mockMvc.perform(unauth.get().header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isUnauthorized());
        }

        // 1B. All allowed roles -> 200 OK; unauthorized role -> 403
        for (Authentication caller : List.of(
                auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD"),
                auth(ACC_BR, MEM_BR, "BRANCH_HEAD"),
                auth(ACC_ORD, MEM_ORD, "MEMBER"),
                auth(ACC_ADMIN, null, "ADMIN")
        )) {
            mockMvc.perform(get("/api/ledger")
                            .with(authentication(caller))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(8));
        }

        mockMvc.perform(get("/api/ledger")
                        .with(authentication(auth(ACC_ORD, MEM_ORD, "GUEST")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isForbidden());

        // 1C. Empty clan -> 200 with zero metrics & null latest refs
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(auth(ACC_ADMIN, null, "ADMIN")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_EMPTY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clanId").value(CLAN_EMPTY))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.summary.currentFundBalance").value(0.00))
                .andExpect(jsonPath("$.data.summary.latestTransactionId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.summary.latestTransactionAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.summary.openingBalanceBeforePeriod").value(0.00))
                .andExpect(jsonPath("$.data.summary.closingBalanceAtPeriodEnd").value(0.00))
                .andExpect(jsonPath("$.data.summary.filteredTransactionCount").value(0))
                .andExpect(jsonPath("$.data.summary.filteredTotalInflow").value(0.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalOutflow").value(0.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(0.00));

        // 1D. Cross-tenant isolation even when querying CLAN_ID eventId/keyword/txId
        Authentication otherAuth = auth(ACC_OTHER, MEM_OTHER, "MEMBER");
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(otherAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_OTHER)
                        .param("eventId", EVT_A)
                        .param("keyword", "Giỗ Tổ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/ledger/transactions/" + TX2)
                        .with(authentication(otherAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_OTHER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("QA Group 2: Wrong Data Types & Malformed Formats -> 400 Bad Request")
    void qaGroup2_wrongDataTypesAndMalformedFormats() throws Exception {
        Authentication headAuth = auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD");

        // 2A. Invalid transactionType values
        for (String badType : List.of("INVALID", "123", "surplustransfer", "SurplusTransfer,INVALID")) {
            mockMvc.perform(get("/api/ledger")
                            .with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .param("transactionType", badType))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(Matchers.containsString("SurplusTransfer")));
        }

        // Repeated transactionType with one valid and one invalid -> clean error message, no [Ljava.lang.String;@
        mockMvc.perform(get("/api/v1/ledger")
                        .with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("transactionType", "SurplusTransfer")
                        .param("transactionType", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.containsString("[Ljava.lang.String;@"))))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("[SurplusTransfer, INVALID]")))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("CommonFundDisbursement")));

        // 2B. Invalid page / size types
        for (String badNum : List.of("abc", "1.5", "999999999999999999")) {
            mockMvc.perform(get("/api/ledger").with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("page", badNum))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/ledger").with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("size", badNum))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(URI.create("/api/ledger?page=%20%20"))
                        .with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isBadRequest());

        // 2C. Invalid onlyWithAdjustments & sortDirection
        for (String badBool : List.of("notabool", "2")) {
            mockMvc.perform(get("/api/ledger").with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("onlyWithAdjustments", badBool))
                    .andExpect(status().isBadRequest());
        }
        for (String badSort : List.of("INVALID", "UP", "123")) {
            mockMvc.perform(get("/api/ledger").with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("sortDirection", badSort))
                    .andExpect(status().isBadRequest());
        }

        // 2D. Invalid date/timestamp formats
        for (String badDate : List.of(
                "not-a-date", "2026-02-30", "2026-02-29", "2026-13-01", "23/11/2026", "2026-11-23T00:00:00"
        )) {
            mockMvc.perform(get("/api/ledger").with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("from", badDate))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(get("/api/ledger").with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("to", badDate))
                    .andExpect(status().isBadRequest());
        }

        // 2E. Inverted from > to (both yyyy-MM-dd and 1-microsecond ISO difference)
        mockMvc.perform(get("/api/ledger").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", "2026-11-30").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/ledger").with(authentication(headAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", "2026-10-10T17:00:00.000001Z")
                        .param("to", "2026-10-10T17:00:00.000000Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("QA Group 3: Empty, Whitespace & Non-Existent References")
    void qaGroup3_emptyWhitespaceAndNonExistentRefs() throws Exception {
        Authentication memberAuth = auth(ACC_ORD, MEM_ORD, "MEMBER");

        // 3A. Empty query params fall back to defaults -> 200 OK (all 8 rows)
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", "").param("to", "").param("transactionType", "")
                        .param("eventId", "").param("keyword", "").param("onlyWithAdjustments", "")
                        .param("sortDirection", "").param("page", "").param("size", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(8));

        // 3B. Whitespace-only optional string params -> 200 OK
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", "   ").param("to", "   ").param("eventId", "   ")
                        .param("keyword", "   ").param("sortDirection", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(8));

        // 3C. Non-existent eventId -> 200 empty items, zero filtered aggregates, real currentFundBalance
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("eventId", "EVT_NOT_EXIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.summary.filteredTransactionCount").value(0))
                .andExpect(jsonPath("$.data.summary.filteredTotalInflow").value(0.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalOutflow").value(0.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(0.00))
                .andExpect(jsonPath("$.data.summary.currentFundBalance").value(120000000.00));

        // 3D. Blank / non-existent / >20-char transactionId -> 404
        for (String missingPath : List.of(
                "/api/ledger/transactions/%20%20",
                "/api/ledger/transactions/NO_SUCH_TX",
                "/api/ledger/transactions/VERY_LONG_NON_EXISTENT_TRANSACTION_ID_12345"
        )) {
            mockMvc.perform(get(URI.create(missingPath))
                            .with(authentication(memberAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        // 3E. Oversized keyword (2,000 chars) -> 200 OK empty
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("keyword", "X".repeat(2000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("QA Group 4: Pagination Boundaries, Offset Integer Overflow Guard & Multi-Type/Sort")
    void qaGroup4_paginationBoundariesOverflowGuardAndMultiTypeSort() throws Exception {
        Authentication memberAuth = auth(ACC_ORD, MEM_ORD, "MEMBER");

        // 4A. Invalid page / size bounds -> 400
        for (int badPage : List.of(-1, -100)) {
            mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("page", String.valueOf(badPage)))
                    .andExpect(status().isBadRequest());
        }
        for (int badSize : List.of(0, -5, 101, 500)) {
            mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("size", String.valueOf(badSize)))
                    .andExpect(status().isBadRequest());
        }

        // 4B. Exact valid boundaries size=1 and size=100 -> 200
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(8))
                .andExpect(jsonPath("$.data.totalPages").value(8));

        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(8))
                .andExpect(jsonPath("$.data.totalPages").value(1));

        // 4C. Out-of-range page -> 200 empty items + accurate summary
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("page", "50").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(8))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(120000000.00));

        // 4D. Integer overflow guard: page * size > Integer.MAX_VALUE MUST return [] (never wrap back to page 0)
        for (String hugePage : List.of("214748365", "107374183")) {
            mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .param("page", hugePage).param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(8));
        }

        // 4E. Case-insensitive sortDirection & Multi-value transactionType deduplication
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("sortDirection", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX1))
                .andExpect(jsonPath("$.data.items[7].transactionId").value(TX8));

        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("sortDirection", "Desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX8));

        // 2 SurplusTransfer + 2 CommonFundDisbursement = 4 items
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("transactionType", "SurplusTransfer,CommonFundDisbursement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4));

        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("transactionType", "SurplusTransfer")
                        .param("transactionType", "CommonFundDisbursement")
                        .param("transactionType", "SurplusTransfer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4));
    }

    @Test
    @DisplayName("QA Group 5: SQL LIKE Wildcard Escaping & SQL Injection Resilience")
    void qaGroup5_sqlLikeWildcardEscapingAndInjectionResilience() throws Exception {
        Authentication memberAuth = auth(ACC_ORD, MEM_ORD, "MEMBER");

        // 5A. Literal '%' matches ONLY TX1 ("Kết dư 100% hoàn chỉnh lễ B")
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX1));

        // 5B. Literal "100%" matches TX1 only, NOT TX2 ("100000000")
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("keyword", "100%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX1));

        // 5C. Literal "____" matches NOTHING (proves '_' wildcard is escaped)
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("keyword", "____"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());

        // 5D. SQLi & backslash payloads execute safely and leave DB intact
        for (String sqli : List.of("' OR 1=1 --", "\\", "\"; DROP TABLE immutable_ledgers; --")) {
            mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("keyword", sqli))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items").isEmpty());
        }

        // 5E. Case-insensitive Vietnamese keyword & partial transactionId search
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("keyword", "giỗ tổ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX2));

        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID).param("keyword", "q62t06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX6));
    }

    @Test
    @DisplayName("QA Group 6: Vietnam +07:00 Midnight Straddle & Exact Accounting Identity")
    void qaGroup6_vietnamTimezoneStraddleAndAccountingIdentity() throws Exception {
        Authentication memberAuth = auth(ACC_ORD, MEM_ORD, "MEMBER");

        // 6A. 2026-10-10 (+07:00) captures TX1 (16:59:59Z) and excludes TX2 (17:00:00Z = 2026-10-11 00:00:00+07:00)
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", "2026-10-10").param("to", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX1))
                .andExpect(jsonPath("$.data.summary.openingBalanceBeforePeriod").value(0.00))
                .andExpect(jsonPath("$.data.summary.closingBalanceAtPeriodEnd").value(50000000.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalInflow").value(50000000.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalOutflow").value(0.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(50000000.00));

        // 6B. 2026-10-11 (+07:00) captures TX2; opening=50M, net=+100M, closing=150M
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", "2026-10-11").param("to", "2026-10-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX2))
                .andExpect(jsonPath("$.data.summary.openingBalanceBeforePeriod").value(50000000.00))
                .andExpect(jsonPath("$.data.summary.closingBalanceAtPeriodEnd").value(150000000.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(100000000.00));

        // 6C. Single instant from == to == T3_OCT12 -> opening (150M) + net (-5M) == closing (145M)
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", T3_OCT12.toString()).param("to", T3_OCT12.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX3))
                .andExpect(jsonPath("$.data.summary.openingBalanceBeforePeriod").value(150000000.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(-5000000.00))
                .andExpect(jsonPath("$.data.summary.closingBalanceAtPeriodEnd").value(145000000.00));

        // 6D. Past & Future empty windows
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", "2020-01-01").param("to", "2020-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.summary.openingBalanceBeforePeriod").value(0.00))
                .andExpect(jsonPath("$.data.summary.closingBalanceAtPeriodEnd").value(0.00))
                .andExpect(jsonPath("$.data.summary.currentFundBalance").value(120000000.00));

        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", "2099-01-01").param("to", "2099-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.summary.openingBalanceBeforePeriod").value(120000000.00))
                .andExpect(jsonPath("$.data.summary.closingBalanceAtPeriodEnd").value(120000000.00))
                .andExpect(jsonPath("$.data.summary.currentFundBalance").value(120000000.00));

        // 6E. OffsetDateTime with +07:00 and -05:00
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", "2026-10-10T23:59:59+07:00")
                        .param("to", "2026-10-10T12:00:00-05:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("QA Group 7: Complex Bidirectional Graph (Multiple, Pending vs Rejected, Nested Adjustment, Disbursement & Cross-Window)")
    void qaGroup7_complexBidirectionalLinks() throws Exception {
        Authentication memberAuth = auth(ACC_ORD, MEM_ORD, "MEMBER");

        // 7A. TX2: 2 approved adjustments, 1 pending (1 rejected ignored) -> netAdjustedAmount = 97M
        mockMvc.perform(get("/api/ledger/transactions/" + TX2)
                        .with(authentication(memberAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isAdjustment").value(false))
                .andExpect(jsonPath("$.data.hasAdjustments").value(true))
                .andExpect(jsonPath("$.data.adjustmentCount").value(2))
                .andExpect(jsonPath("$.data.netAdjustedAmount").value(97000000.00))
                .andExpect(jsonPath("$.data.pendingAdjustmentRequestCount").value(1))
                .andExpect(jsonPath("$.data.adjustmentTransactions[0].transactionId").value(TX3))
                .andExpect(jsonPath("$.data.adjustmentTransactions[1].transactionId").value(TX4));

        // 7B. Nested adjustment node TX3: BOTH originalTransaction=TX2 AND adjustmentTransactions=[TX5]
        mockMvc.perform(get("/api/ledger/transactions/" + TX3)
                        .with(authentication(memberAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isAdjustment").value(true))
                .andExpect(jsonPath("$.data.originalTransaction.transactionId").value(TX2))
                .andExpect(jsonPath("$.data.hasAdjustments").value(true))
                .andExpect(jsonPath("$.data.adjustmentCount").value(1))
                .andExpect(jsonPath("$.data.adjustmentTransactions[0].transactionId").value(TX5))
                .andExpect(jsonPath("$.data.netAdjustedAmount").value(-4000000.00));

        // 7C. Disbursement TX6 adjusted by TX7 -> enrichment + netAdjustedAmount = -17M
        mockMvc.perform(get("/api/ledger/transactions/" + TX6)
                        .with(authentication(memberAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbursementTitle").value("Tu sửa mái Từ đường QA"))
                .andExpect(jsonPath("$.data.disbursementCategory").value("TempleMaintenance"))
                .andExpect(jsonPath("$.data.hasAdjustments").value(true))
                .andExpect(jsonPath("$.data.adjustmentCount").value(1))
                .andExpect(jsonPath("$.data.netAdjustedAmount").value(-17000000.00));

        // 7D. Cross-window links: Oct 12..13 returns [TX3, TX4] only, yet retains original TX2 & child TX5
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", "2026-10-12").param("to", "2026-10-13").param("sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX3))
                .andExpect(jsonPath("$.data.items[0].originalTransaction.transactionId").value(TX2))
                .andExpect(jsonPath("$.data.items[0].adjustmentTransactions[0].transactionId").value(TX5))
                .andExpect(jsonPath("$.data.items[1].transactionId").value(TX4))
                .andExpect(jsonPath("$.data.items[1].originalTransaction.transactionId").value(TX2));
    }

    @Test
    @DisplayName("QA Group 8: onlyWithAdjustments=true/false & Combined Filter Matrix")
    void qaGroup8_onlyWithAdjustmentsAndCombinedFilters() throws Exception {
        Authentication memberAuth = auth(ACC_ORD, MEM_ORD, "MEMBER");

        // onlyWithAdjustments=true -> [TX2, TX3, TX4, TX5, TX6, TX7] = 6 items
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("onlyWithAdjustments", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(6));

        // onlyWithAdjustments=false -> [TX1, TX8] = 2 items
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("onlyWithAdjustments", "false").param("sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX1))
                .andExpect(jsonPath("$.data.items[1].transactionId").value(TX8));

        // Combined: CommonFundDisbursement + onlyWithAdjustments=true + keyword="Từ đường" -> TX6 only
        mockMvc.perform(get("/api/ledger").with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("transactionType", "CommonFundDisbursement")
                        .param("onlyWithAdjustments", "true")
                        .param("keyword", "Từ đường"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(TX6))
                .andExpect(jsonPath("$.data.summary.filteredTotalInflow").value(0.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalOutflow").value(20000000.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(-20000000.00));
    }

    @Test
    @DisplayName("QA Group 9: Display Name Fallback Chain (Member -> Phone -> Email -> AccountId) & Event Enrichment")
    void qaGroup9_displayNameFallbackChain() throws Exception {
        Authentication memberAuth = auth(ACC_ORD, MEM_ORD, "MEMBER");

        // TX1 has createdBy=ACC_PHONE_ONLY -> 0999888777, approvedBy=ACC_EMAIL_ONLY -> emailonly@qa.vn, eventName=Lễ Chạp Mả Q62
        mockMvc.perform(get("/api/ledger/transactions/" + TX1)
                        .with(authentication(memberAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdByName").value("0999888777"))
                .andExpect(jsonPath("$.data.approvedByName").value("emailonly@qa.vn"))
                .andExpect(jsonPath("$.data.eventName").value("Lễ Chạp Mả Q62"));

        // TX5 has createdBy=ACC_BLANK_ALL -> ACCQ62BLANK, approvedBy=ACC_HEAD -> Cụ Trưởng Tộc Q62
        mockMvc.perform(get("/api/ledger/transactions/" + TX5)
                        .with(authentication(memberAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdByName").value(ACC_BLANK_ALL))
                .andExpect(jsonPath("$.data.approvedByName").value("Cụ Trưởng Tộc Q62"));
    }

    @Test
    @DisplayName("QA Group 10: US-61 Immutability Guards, Method Override, Wrong Verbs & Route Non-Collision")
    void qaGroup10_immutabilityGuardsMethodOverrideAndRouteNonCollision() throws Exception {
        Authentication headAuth = auth(ACC_HEAD, MEM_HEAD, "CLAN_HEAD");

        // 10A. PUT / PATCH / DELETE blocked with 405 + Allow + LEDGER_IMMUTABILITY_VIOLATION
        for (Supplier<MockHttpServletRequestBuilder> mut : List.<Supplier<MockHttpServletRequestBuilder>>of(
                () -> put("/api/ledger"),
                () -> patch("/api/ledger"),
                () -> delete("/api/ledger"),
                () -> put("/api/v1/ledger"),
                () -> patch("/api/ledger/immutable"),
                () -> delete("/api/v1/ledger/immutable"),
                () -> put("/api/ledger/transactions/" + TX1),
                () -> patch("/api/ledger/transactions/" + TX1),
                () -> delete("/api/v1/ledger/transactions/" + TX1)
        )) {
            mockMvc.perform(mut.get().with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET, POST, HEAD, OPTIONS"))
                    .andExpect(jsonPath("$.code").value(LedgerImmutabilityViolationException.ERROR_CODE));
        }

        // 10B. X-HTTP-Method-Override header spoof blocked with 405
        for (String overrideMethod : List.of("DELETE", "PUT", "PATCH")) {
            mockMvc.perform(post("/api/ledger/transactions/" + TX1)
                            .with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                            .header("X-HTTP-Method-Override", overrideMethod))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value(LedgerImmutabilityViolationException.ERROR_CODE));
        }

        // 10C. POST on GET-only routes -> 405; GET on POST-only routes -> still 405 (no route collision)
        for (Supplier<MockHttpServletRequestBuilder> wrongVerb : List.<Supplier<MockHttpServletRequestBuilder>>of(
                () -> post("/api/ledger"),
                () -> post("/api/v1/ledger"),
                () -> post("/api/ledger/immutable"),
                () -> post("/api/ledger/transactions/" + TX1),
                () -> get("/api/v1/ledger/review-adjustment"),
                () -> get("/api/v1/ledger/review-disbursement")
        )) {
            mockMvc.perform(wrongVerb.get().with(authentication(headAuth)).header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isMethodNotAllowed());
        }
    }
}
