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
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.entity.LedgerAdjustmentRequest;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.repository.LedgerAdjustmentRequestRepository;
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
class LedgerAdjustmentIntegrationTest {

    private static final String CLAN_ID = "CLN_US15_IT";
    private static final String OTHER_CLAN_ID = "CLN_US15_OTHER";
    private static final String BRANCH_ID = "BR_US15_IT";
    private static final String EVENT_ID = "EVT_US15_IT";
    private static final String TX_ID = "LDG_US15_001";
    private static final String TX_POS_ID = "LDG_US15_002";

    private static final String MEMBER_HEAD = "MEM_US15_HEAD";
    private static final String ACCOUNT_HEAD = "ACC_US15_HEAD";

    private static final String MEMBER_TREASURER = "MEM_US15_TREAS";
    private static final String ACCOUNT_TREASURER = "ACC_US15_TREAS";

    private static final String MEMBER_ORDINARY = "MEM_US15_ORD";
    private static final String ACCOUNT_ORDINARY = "ACC_US15_ORD";

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
    private AccountRoleRepository accountRoleRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventPersonnelRepository eventPersonnelRepository;

    @Autowired
    private ImmutableLedgerRepository immutableLedgerRepository;

    @Autowired
    private LedgerAdjustmentRequestRepository ledgerAdjustmentRequestRepository;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN_ID);

        clanRepository.save(Clan.builder().clanId(CLAN_ID).clanName("Dòng họ US15 IT").build());
        clanRepository.save(Clan.builder().clanId(OTHER_CLAN_ID).clanName("Dòng họ Khác US15").build());

        FamilyBranch branch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_ID).clanId(CLAN_ID).branchName("Chi Gốc").generationLevel(1).build());

        Member head = memberRepository.save(Member.builder()
                .memberId(MEMBER_HEAD).branch(branch).fullName("Cụ Trưởng Tộc").gender("Male").generation(1).status("Living").build());
        Member treasurer = memberRepository.save(Member.builder()
                .memberId(MEMBER_TREASURER).branch(branch).fullName("Thủ Quỹ Sự Kiện").gender("Female").generation(2).status("Living").build());
        Member ordinary = memberRepository.save(Member.builder()
                .memberId(MEMBER_ORDINARY).branch(branch).fullName("Thành Viên Thường").gender("Male").generation(2).status("Living").build());

        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_HEAD).memberId(head.getMemberId()).phoneNumber("0922000001")
                .passwordHash("hash").accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_TREASURER).memberId(treasurer.getMemberId()).phoneNumber("0922000002")
                .passwordHash("hash").accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_ORDINARY).memberId(ordinary.getMemberId()).phoneNumber("0922000003")
                .passwordHash("hash").accountStatus(AccountStatus.Active).build());

        accountRoleRepository.save(AccountRole.builder()
                .id(new AccountRoleId(ACCOUNT_HEAD, SystemRoles.CLAN_HEAD)).build());

        Instant eventDate = Instant.now().minus(2, ChronoUnit.DAYS);
        eventRepository.save(Event.builder()
                .eventId(EVENT_ID).clanId(CLAN_ID).eventName("Đại Lễ Giỗ Tổ US15")
                .eventDatetime(eventDate).location("Từ đường họ Nguyễn")
                .createdBy(ACCOUNT_HEAD).status(EventStatus.Ended).build());

        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVENT_ID).memberId(MEMBER_TREASURER).eventRole(EventRole.EventTreasurer).isReadOnly(false).build());

        immutableLedgerRepository.save(ImmutableLedger.builder()
                .transactionId(TX_ID)
                .clanId(CLAN_ID)
                .eventId(EVENT_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("50000000.00"))
                .runningBalance(new BigDecimal("150000000.00"))
                .description("Kết dư sự kiện Đại Lễ Giỗ Tổ")
                .createdByAccountId(ACCOUNT_TREASURER)
                .approvedByAccountId(ACCOUNT_HEAD)
                .recordHash("hash_us15_001_" + System.nanoTime())
                .build());

        immutableLedgerRepository.save(ImmutableLedger.builder()
                .transactionId(TX_POS_ID)
                .clanId(CLAN_ID)
                .eventId(EVENT_ID)
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("20000000.00"))
                .runningBalance(new BigDecimal("170000000.00"))
                .description("Kết dư đợt 2")
                .createdByAccountId(ACCOUNT_TREASURER)
                .approvedByAccountId(ACCOUNT_HEAD)
                .recordHash("hash_us15_002_" + System.nanoTime())
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
    @DisplayName("US-15 E2E: Thủ quỹ đề xuất bù trừ âm (-5tr) thành công -> lưu DB Pending -> chặn đề xuất Pending thứ 2 -> tra cứu GET 200")
    void proposeAdjustmentAndQuery_e2eSuccess() throws Exception {
        String proposeJson = """
                {
                    "originalTransactionId": "LDG_US15_001",
                    "proposedAdjustmentAmount": -5000000.00,
                    "adjustmentReason": "Tính nhầm hóa đơn thuê rạp và âm thanh",
                    "evidenceAttachmentUrl": "https://storage.familyfund.vn/evidence/receipt-fix.pdf"
                }
                """;

        String responseBody = mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposeJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalTransactionId").value(TX_ID))
                .andExpect(jsonPath("$.data.proposedAdjustmentAmount").value(-5000000.00))
                .andExpect(jsonPath("$.data.approvalStatus").value("Pending"))
                .andExpect(jsonPath("$.data.proposedByAccountId").value(ACCOUNT_TREASURER))
                .andExpect(jsonPath("$.data.evidenceAttachmentUrl").value("https://storage.familyfund.vn/evidence/receipt-fix.pdf"))
                .andReturn().getResponse().getContentAsString();

        String requestId = com.jayway.jsonpath.JsonPath.read(responseBody, "$.data.requestId");
        assertThat(requestId).startsWith("LAR");

        LedgerAdjustmentRequest stored = ledgerAdjustmentRequestRepository.findById(requestId).orElseThrow();
        assertThat(stored.getOriginalTransactionId()).isEqualTo(TX_ID);
        assertThat(stored.getProposedAdjustmentAmount()).isEqualByComparingTo("-5000000.00");
        assertThat(stored.getApprovalStatus()).isEqualTo(LedgerAdjustmentStatus.Pending);

        // Chặn tạo thêm đề xuất Pending thứ 2 trên cùng giao dịch gốc
        mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposeJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Tra cứu chi tiết và danh sách qua GET
        mockMvc.perform(get("/api/ledger/adjustments/" + requestId)
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(requestId))
                .andExpect(jsonPath("$.data.originalTransactionAmount").value(50000000.00));

        mockMvc.perform(get("/api/v1/ledger/adjustments")
                        .param("originalTransactionId", TX_ID)
                        .param("status", "Pending")
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requestId").value(requestId));
    }

    @Test
    @DisplayName("US-15 E2E: Chấp nhận số tiền bù trừ dương (+) không kèm bằng chứng, nhưng từ chối số tiền 0")
    void proposeAdjustment_positiveOptionalEvidence_andZeroRejected() throws Exception {
        String positiveJson = """
                {
                    "originalTransactionId": "LDG_US15_002",
                    "proposedAdjustmentAmount": 3250000.00,
                    "adjustmentReason": "Bổ sung khoản hoàn cọc dịch vụ tiệc"
                }
                """;

        mockMvc.perform(post("/api/v1/ledger/propose-adjustment")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positiveJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.proposedAdjustmentAmount").value(3250000.00))
                .andExpect(jsonPath("$.data.evidenceAttachmentUrl").doesNotExist());

        String zeroJson = """
                {
                    "originalTransactionId": "LDG_US15_001",
                    "proposedAdjustmentAmount": 0.00,
                    "adjustmentReason": "Kiểm tra số tiền bằng 0"
                }
                """;

        mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(zeroJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("US-15 E2E: Thành viên thường bị từ chối 403; truy vấn khác dòng họ trả 404")
    void proposeAdjustment_securityAndMultiTenancy() throws Exception {
        String json = """
                {
                    "originalTransactionId": "LDG_US15_001",
                    "proposedAdjustmentAmount": -1000000.00,
                    "adjustmentReason": "Không có thẩm quyền"
                }
                """;

        mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .with(authentication(auth(ACCOUNT_ORDINARY, MEMBER_ORDINARY, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/ledger/propose-adjustment")
                        .with(authentication(auth(ACCOUNT_HEAD, MEMBER_HEAD, "CLAN_HEAD")))
                        .header(TenantFilter.HEADER_CLAN_ID, OTHER_CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());
    }
}
