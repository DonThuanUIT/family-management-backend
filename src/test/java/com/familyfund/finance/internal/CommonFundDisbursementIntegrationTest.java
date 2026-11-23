package com.familyfund.finance.internal;

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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommonFundDisbursementIntegrationTest {

    private static final String CLAN_ID = "CLN_US60_IT";
    private static final String OTHER_CLAN_ID = "CLN_US60_OTHER";
    private static final String BRANCH_ID = "BR_US60_IT";
    private static final String EVENT_ID = "EVT_US60_IT";
    private static final String BASE_TX_ID = "LDG_US60_BASE";
    private static final String HEAD_RAW_PASSWORD = "ClanHead@123";

    private static final String MEMBER_HEAD = "MEM_US60_HEAD";
    private static final String ACCOUNT_HEAD = "ACC_US60_HEAD";

    private static final String MEMBER_BRANCH_HEAD = "MEM_US60_BR";
    private static final String ACCOUNT_BRANCH_HEAD = "ACC_US60_BR";

    private static final String MEMBER_TREASURER = "MEM_US60_TREAS";
    private static final String ACCOUNT_TREASURER = "ACC_US60_TREAS";

    private static final String MEMBER_ORDINARY = "MEM_US60_ORD";
    private static final String ACCOUNT_ORDINARY = "ACC_US60_ORD";

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
    private CommonFundDisbursementRequestRepository disbursementRepository;

    @Autowired
    private ImmutableLedgerHashCalculator ledgerHashCalculator;

    @Autowired
    private EntityManager entityManager;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN_ID);

        clanRepository.save(Clan.builder().clanId(CLAN_ID).clanName("Dòng họ US60 IT").build());
        clanRepository.save(Clan.builder().clanId(OTHER_CLAN_ID).clanName("Dòng họ Khác US60").build());

        FamilyBranch branch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_ID).clanId(CLAN_ID).branchName("Chi Gốc US60").generationLevel(1).build());

        Member head = memberRepository.save(Member.builder()
                .memberId(MEMBER_HEAD).branch(branch).fullName("Cụ Trưởng Tộc US60").gender("Male").generation(1).status("Living").build());
        Member branchHead = memberRepository.save(Member.builder()
                .memberId(MEMBER_BRANCH_HEAD).branch(branch).fullName("Trưởng Chi US60").gender("Male").generation(2).status("Living").build());
        Member treasurer = memberRepository.save(Member.builder()
                .memberId(MEMBER_TREASURER).branch(branch).fullName("Thủ Quỹ Sự Kiện US60").gender("Female").generation(2).status("Living").build());
        Member ordinary = memberRepository.save(Member.builder()
                .memberId(MEMBER_ORDINARY).branch(branch).fullName("Thành Viên Thường US60").gender("Male").generation(3).status("Living").build());

        String encodedHeadPass = passwordEncoder.encode(HEAD_RAW_PASSWORD);
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_HEAD).memberId(head.getMemberId()).phoneNumber("0933000001")
                .passwordHash(encodedHeadPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_BRANCH_HEAD).memberId(branchHead.getMemberId()).phoneNumber("0933000002")
                .passwordHash(encodedHeadPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_TREASURER).memberId(treasurer.getMemberId()).phoneNumber("0933000003")
                .passwordHash(encodedHeadPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_ORDINARY).memberId(ordinary.getMemberId()).phoneNumber("0933000004")
                .passwordHash(encodedHeadPass).accountStatus(AccountStatus.Active).build());

        accountRoleRepository.save(AccountRole.builder()
                .id(new AccountRoleId(ACCOUNT_HEAD, SystemRoles.CLAN_HEAD)).build());
        accountRoleRepository.save(AccountRole.builder()
                .id(new AccountRoleId(ACCOUNT_BRANCH_HEAD, SystemRoles.BRANCH_HEAD)).build());

        Instant eventDate = Instant.now().minus(5, ChronoUnit.DAYS);
        eventRepository.save(Event.builder()
                .eventId(EVENT_ID).clanId(CLAN_ID).eventName("Đại Lễ Giỗ Tổ US60")
                .eventDatetime(eventDate).location("Từ đường họ Nguyễn")
                .createdBy(ACCOUNT_HEAD).status(EventStatus.Ended).build());

        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVENT_ID).memberId(MEMBER_TREASURER).eventRole(EventRole.EventTreasurer).isReadOnly(false).build());

        Instant baseTime = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        String baseHash = ledgerHashCalculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH,
                BASE_TX_ID, CLAN_ID, EVENT_ID, null, null, null,
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"), new BigDecimal("100000000.00"),
                "Số dư Quỹ chung ban đầu", ACCOUNT_TREASURER, ACCOUNT_HEAD, baseTime
        );
        immutableLedgerRepository.save(ImmutableLedger.builder()
                .transactionId(BASE_TX_ID)
                .clanId(CLAN_ID)
                .eventId(EVENT_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00"))
                .runningBalance(new BigDecimal("100000000.00"))
                .description("Số dư Quỹ chung ban đầu")
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

    @Test
    @DisplayName("US-60 E2E: Kiểm tra số dư -> 2 đề xuất Pending song song -> sai mật khẩu chặn 400 -> duyệt đúng mật khẩu trừ số dư & nối SHA-256 -> phiếu 2 vượt số dư còn lại bị chặn -> từ chối & tra cứu")
    void fullCommonFundDisbursementLifecycle_e2eSuccess() throws Exception {
        // 1. Lấy số dư khả dụng ban đầu = 100tr
        mockMvc.perform(get("/api/ledger/balance")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableBalance").value(100000000.00))
                .andExpect(jsonPath("$.data.pendingDisbursementAmount").value(0.00));

        // 2. EventTreasurer đề xuất phiếu 1 (60tr) & Trưởng chi đề xuất phiếu 2 (50tr) -> đều <= 100tr nên hợp lệ Pending
        String propose1Res = mockMvc.perform(post("/api/ledger/disbursements")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Tu sửa mái ngói Từ đường",
                                    "category": "TempleMaintenance",
                                    "amount": 60000000.00,
                                    "beneficiaryName": "Đội thợ nề Đồng Kỵ",
                                    "purpose": "Thay thế ngói âm dương dột nát khu vực hậu cung",
                                    "attachmentUrl": "https://storage.familyfund.vn/quotes/roof.pdf"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.approvalStatus").value("Pending"))
                .andReturn().getResponse().getContentAsString();
        String reqId1 = com.jayway.jsonpath.JsonPath.read(propose1Res, "$.data.requestId");
        assertThat(reqId1).startsWith("CFD");

        String propose2Res = mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .with(authentication(auth(ACCOUNT_BRANCH_HEAD, MEMBER_BRANCH_HEAD, "BRANCH_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Học bổng khuyến học toàn tộc 2026",
                                    "category": "Scholarship",
                                    "amount": 50000000.00,
                                    "beneficiaryMemberId": "%s",
                                    "purpose": "Trao thưởng con cháu đỗ đại học"
                                }
                                """.formatted(MEMBER_ORDINARY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.beneficiaryName").value("Thành Viên Thường US60"))
                .andReturn().getResponse().getContentAsString();
        String reqId2 = com.jayway.jsonpath.JsonPath.read(propose2Res, "$.data.requestId");

        // Kiểm tra pendingDisbursementAmount = 110tr
        mockMvc.perform(get("/api/ledger/balance")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingDisbursementAmount").value(110000000.00));

        // 3. Trưởng tộc duyệt phiếu 1 nhưng nhập sai mật khẩu -> 400 Bad Request, không ghi sổ
        mockMvc.perform(post("/api/ledger/disbursements/" + reqId1 + "/review")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "approved": true,
                                    "confirmPassword": "WrongPassword999"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 4. Trưởng tộc duyệt phiếu 1 với mật khẩu đúng -> 200 OK, ghi CommonFundDisbursement (-60tr), running_balance = 40tr
        String approve1Res = mockMvc.perform(post("/api/ledger/disbursements/" + reqId1 + "/review")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "approved": true,
                                    "confirmPassword": "%s"
                                }
                                """.formatted(HEAD_RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbursementRequest.approvalStatus").value("Approved"))
                .andExpect(jsonPath("$.data.ledgerEntry.transactionType").value("CommonFundDisbursement"))
                .andExpect(jsonPath("$.data.ledgerEntry.disbursementRequestId").value(reqId1))
                .andExpect(jsonPath("$.data.ledgerEntry.amount").value(-60000000.00))
                .andExpect(jsonPath("$.data.ledgerEntry.runningBalance").value(40000000.00))
                .andReturn().getResponse().getContentAsString();
        String txId1 = com.jayway.jsonpath.JsonPath.read(approve1Res, "$.data.ledgerEntry.transactionId");

        entityManager.flush();
        entityManager.clear();

        ImmutableLedger baseFromDb = immutableLedgerRepository.findById(BASE_TX_ID).orElseThrow();
        ImmutableLedger disbursementEntryFromDb = immutableLedgerRepository.findById(txId1).orElseThrow();
        assertThat(disbursementEntryFromDb.getPreviousHash()).isEqualTo(baseFromDb.getRecordHash());
        assertThat(ledgerHashCalculator.verifyRecordHash(disbursementEntryFromDb, baseFromDb.getRecordHash())).isTrue();

        // 5. Duyệt phiếu 2 (50tr > số dư còn lại 40tr) -> bị chặn 400 Bad Request, phiếu 2 vẫn Pending
        mockMvc.perform(post("/api/ledger/disbursements/" + reqId2 + "/review")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "approved": true,
                                    "confirmPassword": "%s"
                                }
                                """.formatted(HEAD_RAW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        CommonFundDisbursementRequest req2AfterFail = disbursementRepository.findById(reqId2).orElseThrow();
        assertThat(req2AfterFail.getApprovalStatus()).isEqualTo(CommonFundDisbursementStatus.Pending);

        // 6. Trưởng tộc từ chối phiếu 2 qua alias /api/v1/ledger/review-disbursement (không cần confirmPassword)
        mockMvc.perform(post("/api/v1/ledger/review-disbursement")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_id": "%s",
                                    "approved": false,
                                    "rejection_reason": "Số dư quỹ còn lại không đủ, đề nghị giảm định mức xuống 30tr"
                                }
                                """.formatted(reqId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbursementRequest.approvalStatus").value("Rejected"))
                .andExpect(jsonPath("$.data.ledgerEntry").doesNotExist());

        // 7. Tra cứu chi tiết và lọc danh sách
        mockMvc.perform(get("/api/ledger/disbursements/" + reqId1)
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerTransactionId").value(txId1));

        mockMvc.perform(get("/api/ledger/disbursements")
                        .param("status", "Approved")
                        .param("category", "TempleMaintenance")
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requestId").value(reqId1));
    }

    @Test
    @DisplayName("US-60 E2E: Đề xuất vượt số dư bị chặn 400; RBAC 403 và Multi-tenancy 404 hoạt động chính xác")
    void proposeDisbursement_balanceGuardSecurityAndMultiTenancy() throws Exception {
        // Thành viên thường không phải EventTreasurer -> 403 khi lập đề xuất
        mockMvc.perform(post("/api/ledger/disbursements")
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Đề xuất trái thẩm quyền",
                                    "category": "Other",
                                    "amount": 1000000.00,
                                    "purpose": "Thử quyền thành viên thường"
                                }
                                """))
                .andExpect(status().isForbidden());

        // EventTreasurer đề xuất vượt số dư 100tr -> 400 Bad Request
        mockMvc.perform(post("/api/ledger/disbursements")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Tu sửa lớn vượt quỹ",
                                    "category": "TempleMaintenance",
                                    "amount": 120000000.00,
                                    "purpose": "Vượt quá số dư khả dụng 100tr"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // EventTreasurer lập đề xuất hợp lệ 10tr
        String okRes = mockMvc.perform(post("/api/ledger/disbursements")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Cứu trợ gia đình khó khăn",
                                    "category": "ReliefSupport",
                                    "amount": 10000000.00,
                                    "purpose": "Hỗ trợ hộ gia đình gặp thiên tai"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reqId = com.jayway.jsonpath.JsonPath.read(okRes, "$.data.requestId");

        // EventTreasurer không được duyệt -> 403
        mockMvc.perform(post("/api/ledger/disbursements/" + reqId + "/review")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "approved": true,
                                    "confirmPassword": "%s"
                                }
                                """.formatted(HEAD_RAW_PASSWORD)))
                .andExpect(status().isForbidden());

        // Truy vấn/duyệt khác dòng họ -> 404 Not Found
        mockMvc.perform(post("/api/ledger/disbursements/" + reqId + "/review")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, OTHER_CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "approved": true,
                                    "confirmPassword": "%s"
                                }
                                """.formatted(HEAD_RAW_PASSWORD)))
                .andExpect(status().isNotFound());
    }
}
