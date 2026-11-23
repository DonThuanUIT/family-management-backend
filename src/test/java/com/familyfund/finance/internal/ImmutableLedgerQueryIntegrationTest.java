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

import java.math.BigDecimal;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImmutableLedgerQueryIntegrationTest {

    private static final String CLAN_ID = "CLN_US62_IT";
    private static final String CLAN_OTHER = "CLN_US62_OTHER";
    private static final String BRANCH_ID = "BR_US62_IT";
    private static final String BRANCH_OTHER = "BR_US62_OTHER";
    private static final String EVENT_ID = "EVT_US62_IT";
    private static final String BASE_TX_ID = "LDG_US62_BASE";
    private static final String HEAD_RAW_PASSWORD = "ClanHeadUs62@123";

    private static final String MEMBER_HEAD = "MEM_US62_HEAD";
    private static final String ACCOUNT_HEAD = "ACC_US62_HEAD";

    private static final String MEMBER_TREASURER = "MEM_US62_TREAS";
    private static final String ACCOUNT_TREASURER = "ACC_US62_TREAS";

    private static final String MEMBER_ORDINARY = "MEM_US62_ORD";
    private static final String ACCOUNT_ORDINARY = "ACC_US62_ORD";

    private static final String MEMBER_OTHER = "MEM_US62_OTH";
    private static final String ACCOUNT_OTHER = "ACC_US62_OTH";

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

    private Instant baseTime;
    private String adjTxId1;
    private String adjTxId2;
    private String disbTxId;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seedAndExecuteFlow() throws Exception {
        TenantContext.setClanId(CLAN_ID);

        clanRepository.save(Clan.builder().clanId(CLAN_ID).clanName("Dòng họ US62 Minh Bạch").build());
        clanRepository.save(Clan.builder().clanId(CLAN_OTHER).clanName("Dòng họ US62 Khác").build());

        FamilyBranch branch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_ID).clanId(CLAN_ID).branchName("Chi Gốc US62").generationLevel(1).build());
        FamilyBranch branchOther = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_OTHER).clanId(CLAN_OTHER).branchName("Chi Khác US62").generationLevel(1).build());

        Member head = memberRepository.save(Member.builder()
                .memberId(MEMBER_HEAD).branch(branch).fullName("Cụ Trưởng Tộc US62").gender("Male").generation(1).status("Living").build());
        Member treasurer = memberRepository.save(Member.builder()
                .memberId(MEMBER_TREASURER).branch(branch).fullName("Thủ Quỹ US62").gender("Female").generation(2).status("Living").build());
        Member ordinary = memberRepository.save(Member.builder()
                .memberId(MEMBER_ORDINARY).branch(branch).fullName("Thành Viên US62").gender("Male").generation(3).status("Living").build());
        Member otherMember = memberRepository.save(Member.builder()
                .memberId(MEMBER_OTHER).branch(branchOther).fullName("Thành Viên Họ Khác").gender("Male").generation(2).status("Living").build());

        String encodedPass = passwordEncoder.encode(HEAD_RAW_PASSWORD);
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_HEAD).memberId(head.getMemberId()).phoneNumber("0962000001")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_TREASURER).memberId(treasurer.getMemberId()).phoneNumber("0962000002")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_ORDINARY).memberId(ordinary.getMemberId()).phoneNumber("0962000003")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_OTHER).memberId(otherMember.getMemberId()).phoneNumber("0962000004")
                .passwordHash(encodedPass).accountStatus(AccountStatus.Active).build());

        accountRoleRepository.save(AccountRole.builder()
                .id(new AccountRoleId(ACCOUNT_HEAD, SystemRoles.CLAN_HEAD)).build());

        eventRepository.save(Event.builder()
                .eventId(EVENT_ID).clanId(CLAN_ID).eventName("Đại Lễ Giỗ Tổ US62")
                .eventDatetime(Instant.now().minus(5, ChronoUnit.DAYS)).location("Từ đường US62")
                .createdBy(ACCOUNT_HEAD).status(EventStatus.Ended).build());
        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVENT_ID).memberId(MEMBER_TREASURER).eventRole(EventRole.EventTreasurer).isReadOnly(false).build());

        // 1. Seed initial SurplusTransfer (+100,000,000)
        baseTime = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        String baseHash = ledgerHashCalculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH,
                BASE_TX_ID, CLAN_ID, EVENT_ID, null, null, null,
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"), new BigDecimal("100000000.00"),
                "Kết dư Đại Lễ Giỗ Tổ US62", ACCOUNT_TREASURER, ACCOUNT_HEAD, baseTime
        );
        immutableLedgerRepository.saveAndFlush(ImmutableLedger.builder()
                .transactionId(BASE_TX_ID)
                .clanId(CLAN_ID)
                .eventId(EVENT_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00"))
                .runningBalance(new BigDecimal("100000000.00"))
                .description("Kết dư Đại Lễ Giỗ Tổ US62")
                .createdByAccountId(ACCOUNT_TREASURER)
                .approvedByAccountId(ACCOUNT_HEAD)
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH)
                .recordHash(baseHash)
                .createdAt(baseTime)
                .build());

        // 2. Propose & approve Adjustment #1 (-5,000,000)
        String propAdj1 = mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "originalTransactionId": "%s",
                                    "proposedAdjustmentAmount": -5000000.00,
                                    "adjustmentReason": "Trừ hóa đơn âm thanh ánh sáng",
                                    "evidenceAttachmentUrl": "https://cdn.familyfund.vn/us62-adj1.pdf"
                                }
                                """.formatted(BASE_TX_ID)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String adjReqId1 = JsonPath.read(propAdj1, "$.data.requestId");

        String revAdj1 = mockMvc.perform(post("/api/ledger/review-adjustment")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "requestId": "%s",
                                    "approved": true
                                }
                                """.formatted(adjReqId1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adjTxId1 = JsonPath.read(revAdj1, "$.data.ledgerEntry.transactionId");

        // 3. Propose & approve Adjustment #2 (+2,000,000)
        String propAdj2 = mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "originalTransactionId": "%s",
                                    "proposedAdjustmentAmount": 2000000.00,
                                    "adjustmentReason": "Bổ sung tiền công đức thu muộn",
                                    "evidenceAttachmentUrl": "https://cdn.familyfund.vn/us62-adj2.pdf"
                                }
                                """.formatted(BASE_TX_ID)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String adjReqId2 = JsonPath.read(propAdj2, "$.data.requestId");

        String revAdj2 = mockMvc.perform(post("/api/ledger/review-adjustment")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "requestId": "%s",
                                    "approved": true
                                }
                                """.formatted(adjReqId2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adjTxId2 = JsonPath.read(revAdj2, "$.data.ledgerEntry.transactionId");

        // 4. Propose 1 Pending Adjustment Request on BASE_TX_ID (not yet reviewed)
        mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "originalTransactionId": "%s",
                                    "proposedAdjustmentAmount": -1000000.00,
                                    "adjustmentReason": "Phiếu điều chỉnh đang chờ Trưởng tộc duyệt"
                                }
                                """.formatted(BASE_TX_ID)))
                .andExpect(status().isCreated());

        // 5. Propose & approve 1 CommonFundDisbursement (-20,000,000) -> final runningBalance = 77,000,000
        String propDisb = mockMvc.perform(post("/api/v1/ledger/disbursements")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Tu sửa cổng Từ đường US62",
                                    "category": "TempleMaintenance",
                                    "amount": 20000000.00,
                                    "purpose": "Sơn sửa cổng tam quan"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String disbReqId = JsonPath.read(propDisb, "$.data.requestId");

        String revDisb = mockMvc.perform(post("/api/v1/ledger/disbursements/" + disbReqId + "/review")
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
                .andReturn().getResponse().getContentAsString();
        disbTxId = JsonPath.read(revDisb, "$.data.ledgerEntry.transactionId");

        entityManager.flush();
        entityManager.clear();
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
    @DisplayName("US-62 E2E: Danh sách đầy đủ, phân trang page/size, sắp xếp ASC/DESC và tổng hợp dòng tiền trên PostgreSQL")
    void getLedgerHistory_fullListPaginationAndSorting() throws Exception {
        Authentication memberAuth = auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER");

        // 1. Default DESC: [disbTxId, adjTxId2, adjTxId1, BASE_TX_ID]
        // Total inflow = 100M + 2M = 102M; Total outflow = 5M + 20M = 25M; Net = 77M; Current balance = 77M
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.clanId").value(CLAN_ID))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(disbTxId))
                .andExpect(jsonPath("$.data.items[0].disbursementTitle").value("Tu sửa cổng Từ đường US62"))
                .andExpect(jsonPath("$.data.items[0].disbursementCategory").value("TempleMaintenance"))
                .andExpect(jsonPath("$.data.items[3].transactionId").value(BASE_TX_ID))
                .andExpect(jsonPath("$.data.summary.currentFundBalance").value(77000000.00))
                .andExpect(jsonPath("$.data.summary.latestTransactionId").value(disbTxId))
                .andExpect(jsonPath("$.data.summary.filteredTransactionCount").value(4))
                .andExpect(jsonPath("$.data.summary.filteredTotalInflow").value(102000000.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalOutflow").value(25000000.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(77000000.00));

        // 2. ASC with pagination size=2, page=0 -> [BASE_TX_ID, adjTxId1]
        mockMvc.perform(get("/api/v1/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("sortDirection", "ASC")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(BASE_TX_ID))
                .andExpect(jsonPath("$.data.items[1].transactionId").value(adjTxId1));
    }

    @Test
    @DisplayName("US-62 E2E Liên kết 2 chiều khi lọc theo transactionType: AdjustmentTransaction giữ nguyên originalTransaction và SurplusTransfer giữ nguyên 2 adjustmentTransactions")
    void getLedgerHistory_bidirectionalLinksPreservedUnderTypeFilter() throws Exception {
        Authentication memberAuth = auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER");

        // Chiều 1: Lọc transactionType=AdjustmentTransaction -> trả về 2 bản ghi điều chỉnh, mỗi bản ghi vẫn chứa originalTransaction trỏ về BASE_TX_ID
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("transactionType", "AdjustmentTransaction")
                        .param("sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(adjTxId1))
                .andExpect(jsonPath("$.data.items[0].isAdjustment").value(true))
                .andExpect(jsonPath("$.data.items[0].originalTransactionId").value(BASE_TX_ID))
                .andExpect(jsonPath("$.data.items[0].adjustmentReason").value("Trừ hóa đơn âm thanh ánh sáng"))
                .andExpect(jsonPath("$.data.items[0].evidenceAttachmentUrl").value("https://cdn.familyfund.vn/us62-adj1.pdf"))
                .andExpect(jsonPath("$.data.items[0].originalTransaction.transactionId").value(BASE_TX_ID))
                .andExpect(jsonPath("$.data.items[0].originalTransaction.amount").value(100000000.00))
                .andExpect(jsonPath("$.data.items[0].originalTransaction.runningBalance").value(100000000.00))
                .andExpect(jsonPath("$.data.items[0].originalTransaction.createdByName").value("Thủ Quỹ US62"))
                .andExpect(jsonPath("$.data.items[0].originalTransaction.approvedByName").value("Cụ Trưởng Tộc US62"))
                .andExpect(jsonPath("$.data.items[1].transactionId").value(adjTxId2))
                .andExpect(jsonPath("$.data.items[1].originalTransaction.transactionId").value(BASE_TX_ID))
                .andExpect(jsonPath("$.data.summary.filteredTransactionCount").value(2))
                .andExpect(jsonPath("$.data.summary.filteredTotalInflow").value(2000000.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalOutflow").value(5000000.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(-3000000.00));

        // Chiều 2: Lọc transactionType=SurplusTransfer -> chỉ trả về BASE_TX_ID nhưng vẫn gắn đầy đủ 2 adjustmentTransactions, netAdjustedAmount=97,000,000.00 và pendingAdjustmentRequestCount=1
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("transactionType", "SurplusTransfer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(BASE_TX_ID))
                .andExpect(jsonPath("$.data.items[0].eventName").value("Đại Lễ Giỗ Tổ US62"))
                .andExpect(jsonPath("$.data.items[0].createdByName").value("Thủ Quỹ US62"))
                .andExpect(jsonPath("$.data.items[0].approvedByName").value("Cụ Trưởng Tộc US62"))
                .andExpect(jsonPath("$.data.items[0].isAdjustment").value(false))
                .andExpect(jsonPath("$.data.items[0].hasAdjustments").value(true))
                .andExpect(jsonPath("$.data.items[0].adjustmentCount").value(2))
                .andExpect(jsonPath("$.data.items[0].netAdjustedAmount").value(97000000.00))
                .andExpect(jsonPath("$.data.items[0].pendingAdjustmentRequestCount").value(1))
                .andExpect(jsonPath("$.data.items[0].adjustmentTransactions.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].adjustmentTransactions[0].transactionId").value(adjTxId1))
                .andExpect(jsonPath("$.data.items[0].adjustmentTransactions[0].amount").value(-5000000.00))
                .andExpect(jsonPath("$.data.items[0].adjustmentTransactions[0].adjustmentReason").value("Trừ hóa đơn âm thanh ánh sáng"))
                .andExpect(jsonPath("$.data.items[0].adjustmentTransactions[1].transactionId").value(adjTxId2))
                .andExpect(jsonPath("$.data.items[0].adjustmentTransactions[1].amount").value(2000000.00));
    }

    @Test
    @DisplayName("US-62 E2E: Lọc theo khoảng thời gian from/to, onlyWithAdjustments=true/false và chi tiết giao dịch GET /api/ledger/transactions/{id}")
    void getLedgerHistory_timeWindowOnlyWithAdjustmentsAndTransactionDetail() throws Exception {
        Authentication memberAuth = auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER");

        // 1. Khoảng thời gian bắt đầu sau BASE_TX_ID (baseTime + 30 phút) -> openingBalanceBeforePeriod = 100,000,000.00
        String windowFrom = baseTime.plus(30, ChronoUnit.MINUTES).toString();
        String windowTo = Instant.now().plus(5, ChronoUnit.MINUTES).toString();

        mockMvc.perform(get("/api/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("from", windowFrom)
                        .param("to", windowTo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.summary.openingBalanceBeforePeriod").value(100000000.00))
                .andExpect(jsonPath("$.data.summary.closingBalanceAtPeriodEnd").value(77000000.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalInflow").value(2000000.00))
                .andExpect(jsonPath("$.data.summary.filteredTotalOutflow").value(25000000.00))
                .andExpect(jsonPath("$.data.summary.filteredNetAmount").value(-23000000.00));

        // 2. onlyWithAdjustments=true -> trả về 3 bản ghi (BASE_TX_ID + 2 AdjustmentTransaction)
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("onlyWithAdjustments", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        // 3. onlyWithAdjustments=false -> chỉ trả về 1 bản ghi (disbTxId không bị điều chỉnh và không phải điều chỉnh)
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .param("onlyWithAdjustments", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].transactionId").value(disbTxId));

        // 4. GET /api/ledger/transactions/{transactionId} trên giao dịch gốc và giao dịch điều chỉnh
        mockMvc.perform(get("/api/ledger/transactions/" + BASE_TX_ID)
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value(BASE_TX_ID))
                .andExpect(jsonPath("$.data.hasAdjustments").value(true))
                .andExpect(jsonPath("$.data.adjustmentCount").value(2))
                .andExpect(jsonPath("$.data.netAdjustedAmount").value(97000000.00))
                .andExpect(jsonPath("$.data.pendingAdjustmentRequestCount").value(1))
                .andExpect(jsonPath("$.data.adjustmentTransactions.length()").value(2));

        mockMvc.perform(get("/api/ledger/transactions/" + adjTxId1)
                        .with(authentication(memberAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionId").value(adjTxId1))
                .andExpect(jsonPath("$.data.isAdjustment").value(true))
                .andExpect(jsonPath("$.data.originalTransaction.transactionId").value(BASE_TX_ID))
                .andExpect(jsonPath("$.data.originalTransaction.amount").value(100000000.00));
    }

    @Test
    @DisplayName("US-62 E2E Bảo mật: Cô lập đa dòng họ (Cross-tenant isolation) và chặn HTTP PUT/PATCH/DELETE với mã LEDGER_IMMUTABILITY_VIOLATION (405)")
    void getLedgerHistory_crossTenantIsolationAndHttpImmutabilityGuard() throws Exception {
        Authentication otherClanAuth = auth(ACCOUNT_OTHER, MEMBER_OTHER, "MEMBER");

        // 1. Dòng họ khác truy vấn GET /api/ledger -> danh sách rỗng
        mockMvc.perform(get("/api/ledger")
                        .with(authentication(otherClanAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_OTHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clanId").value(CLAN_OTHER))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.summary.currentFundBalance").value(0.00));

        // 2. Dòng họ khác truy vấn chi tiết giao dịch của CLAN_ID -> 404 Not Found
        mockMvc.perform(get("/api/ledger/transactions/" + BASE_TX_ID)
                        .with(authentication(otherClanAuth))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_OTHER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        // 3. HTTP Immutability Guard: PUT/PATCH/DELETE trên /api/ledger và /api/ledger/transactions/{id} -> 405 Method Not Allowed
        Authentication headAuth = auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD");
        List<Supplier<MockHttpServletRequestBuilder>> forbiddenMutations = List.of(
                () -> put("/api/ledger"),
                () -> patch("/api/ledger"),
                () -> delete("/api/ledger"),
                () -> put("/api/ledger/transactions/" + BASE_TX_ID),
                () -> patch("/api/ledger/transactions/" + BASE_TX_ID),
                () -> delete("/api/ledger/transactions/" + BASE_TX_ID),
                () -> delete("/api/v1/ledger/transactions/" + BASE_TX_ID)
        );

        for (Supplier<MockHttpServletRequestBuilder> req : forbiddenMutations) {
            mockMvc.perform(req.get()
                            .with(authentication(headAuth))
                            .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "GET, POST, HEAD, OPTIONS"))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(LedgerImmutabilityViolationException.ERROR_CODE));
        }
    }
}
