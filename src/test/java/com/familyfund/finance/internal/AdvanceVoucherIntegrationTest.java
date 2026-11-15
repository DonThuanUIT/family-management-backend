package com.familyfund.finance.internal;

import com.familyfund.core.security.MemberLinkedPrincipal;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.tenant.TenantFilter;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.entity.EventPersonnelId;
import com.familyfund.event.internal.entity.EventTask;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.internal.entity.AdvanceVoucher;
import com.familyfund.finance.internal.repository.AdvanceVoucherRepository;
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
class AdvanceVoucherIntegrationTest {

    private static final String CLAN_ID = "CLN_US46_IT";
    private static final String EVENT_ID = "EVT_US46_IT";
    private static final String TASK_ID = "TSK_US46_IT";
    private static final String BRANCH_ID = "BR_US46_IT";

    private static final String MEMBER_BTC = "MEM_US46_BTC";
    private static final String ACCOUNT_BTC = "ACC_US46_BTC";

    private static final String MEMBER_TREASURER = "MEM_US46_TREASURER";
    private static final String ACCOUNT_TREASURER = "ACC_US46_TREASURER";

    private static final String MEMBER_RECIPIENT = "MEM_US46_RECIPIENT";
    private static final String ACCOUNT_RECIPIENT = "ACC_US46_RECIPIENT";

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
    private EventRepository eventRepository;

    @Autowired
    private EventTaskRepository eventTaskRepository;

    @Autowired
    private EventPersonnelRepository eventPersonnelRepository;

    @Autowired
    private AdvanceVoucherRepository advanceVoucherRepository;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN_ID);

        clanRepository.save(Clan.builder().clanId(CLAN_ID).clanName("Dòng họ US46 IT").build());

        FamilyBranch branch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_ID).clanId(CLAN_ID).branchName("Chi Một").generationLevel(1).build());

        Member btcMember = memberRepository.save(Member.builder()
                .memberId(MEMBER_BTC).branch(branch).fullName("Trưởng Ban BTC").gender("Male").generation(1).status("Living").build());
        Member treasurerMember = memberRepository.save(Member.builder()
                .memberId(MEMBER_TREASURER).branch(branch).fullName("Thủ Quỹ Sự Kiện").gender("Female").generation(1).status("Living").build());
        Member recipientMember = memberRepository.save(Member.builder()
                .memberId(MEMBER_RECIPIENT).branch(branch).fullName("Người Nhận Tạm Ứng").gender("Male").generation(2).status("Living").build());

        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_BTC).memberId(btcMember.getMemberId()).phoneNumber("0911000001").passwordHash("hash").accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_TREASURER).memberId(treasurerMember.getMemberId()).phoneNumber("0911000002").passwordHash("hash").accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_RECIPIENT).memberId(recipientMember.getMemberId()).phoneNumber("0911000003").passwordHash("hash").accountStatus(AccountStatus.Active).build());

        Instant eventDate = Instant.now().plus(10, ChronoUnit.DAYS);
        eventRepository.save(Event.builder()
                .eventId(EVENT_ID).clanId(CLAN_ID).eventName("Lễ Hội Dòng Họ US46")
                .eventDatetime(eventDate).location("Từ đường họ Nguyễn")
                .createdBy(ACCOUNT_BTC).status(EventStatus.RegistrationOpen).build());

        eventTaskRepository.save(EventTask.builder()
                .taskId(TASK_ID).eventId(EVENT_ID).taskName("Chuẩn bị mâm cỗ")
                .advanceBudget(new BigDecimal("15000000.00")).deadline(eventDate.minus(1, ChronoUnit.DAYS))
                .status(com.familyfund.event.api.dto.TaskStatus.InProgress)
                .assignedMemberId(MEMBER_RECIPIENT).createdBy(ACCOUNT_BTC).build());

        // Contextual roles in event personnel:
        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVENT_ID).memberId(MEMBER_BTC).eventRole(EventRole.HeadOfOrganizer).isReadOnly(false).build());
        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVENT_ID).memberId(MEMBER_TREASURER).eventRole(EventRole.EventTreasurer).isReadOnly(false).build());
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
    @DisplayName("US-46 E2E Flow: BTC lập phiếu tạm ứng -> Thủ quỹ xác nhận đã giao tiền -> Trạng thái ChuaTatToan")
    void fullAdvanceVoucherFlow_success() throws Exception {
        // 1. BTC (HeadOfOrganizer) lập phiếu tạm ứng POST /api/v1/advances
        String createJson = """
                {
                    "taskId": "TSK_US46_IT",
                    "recipientMemberId": "MEM_US46_RECIPIENT",
                    "advanceAmount": 5000000.00,
                    "receivedDate": "2026-04-01T08:00:00Z",
                    "settlementDeadline": "2026-04-10T17:00:00Z",
                    "commitmentNotes": "Cam kết chi tiêu theo định mức mâm cỗ"
                }
                """;

        String createResponse = mockMvc.perform(post("/api/v1/advances")
                        .with(authentication(auth(ACCOUNT_BTC, MEMBER_BTC, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ChoGiaoTien"))
                .andExpect(jsonPath("$.data.advanceAmount").value(5000000.00))
                .andExpect(jsonPath("$.data.recipientMemberId").value(MEMBER_RECIPIENT))
                .andReturn().getResponse().getContentAsString();

        // Trích xuất mã phiếu tạm ứng đã sinh
        String voucherId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.advanceVoucherId");
        assertThat(voucherId).startsWith("ADV");

        // Kiểm tra database: phiếu tồn tại với status ChoGiaoTien
        AdvanceVoucher stored = advanceVoucherRepository.findById(voucherId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(AdvanceVoucherStatus.ChoGiaoTien);
        assertThat(stored.getAdvanceAmount()).isEqualByComparingTo("5000000.00");
        assertThat(stored.getTreasurerAccountId()).isNull();

        // 2. Thủ quỹ (EventTreasurer) xác nhận đã giao tiền POST /api/v1/advances/{id}/disburse
        String disburseJson = """
                {
                    "notes": "Đã giao tiền mặt 5.000.000đ tại từ đường"
                }
                """;

        mockMvc.perform(post("/api/v1/advances/" + voucherId + "/disburse")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ChuaTatToan"))
                .andExpect(jsonPath("$.data.treasurerAccountId").value(ACCOUNT_TREASURER))
                .andExpect(jsonPath("$.data.disbursedAt").isNotEmpty());

        // Kiểm tra database: trạng thái đã chuyển sang ChuaTatToan
        AdvanceVoucher updated = advanceVoucherRepository.findById(voucherId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AdvanceVoucherStatus.ChuaTatToan);
        assertThat(updated.getTreasurerAccountId()).isEqualTo(ACCOUNT_TREASURER);
        assertThat(updated.getDisbursedAt()).isNotNull();

        // 3. Thao tác lại lần nữa trên phiếu đã giao tiền phải bị chặn (400 Bad Request)
        mockMvc.perform(post("/api/v1/advances/" + voucherId + "/disburse")
                        .with(authentication(auth(ACCOUNT_TREASURER, MEMBER_TREASURER, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 4. Tra cứu chi tiết GET /api/v1/advances/{id}
        mockMvc.perform(get("/api/v1/advances/" + voucherId)
                        .with(authentication(auth(ACCOUNT_RECIPIENT, MEMBER_RECIPIENT, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ChuaTatToan"))
                .andExpect(jsonPath("$.data.recipientName").value("Người Nhận Tạm Ứng"))
                .andExpect(jsonPath("$.data.taskName").value("Chuẩn bị mâm cỗ"));
    }

    @Test
    @DisplayName("US-46: Thành viên thường (không phải BTC) không có quyền lập phiếu tạm ứng (403 Forbidden)")
    void memberWithoutBtcRole_cannotCreateAdvance() throws Exception {
        String createJson = """
                {
                    "taskId": "TSK_US46_IT",
                    "recipientMemberId": "MEM_US46_RECIPIENT",
                    "advanceAmount": 1000000.00,
                    "receivedDate": "2026-04-01T08:00:00Z",
                    "settlementDeadline": "2026-04-10T17:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/advances")
                        .with(authentication(auth(ACCOUNT_RECIPIENT, MEMBER_RECIPIENT, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US-46: Trưởng ban tổ chức (không phải Thủ quỹ) không có quyền xác nhận giao tiền (403 Forbidden)")
    void nonTreasurer_cannotConfirmDisbursement() throws Exception {
        AdvanceVoucher voucher = advanceVoucherRepository.save(AdvanceVoucher.builder()
                .advanceVoucherId("ADV_IT_DISBURSE_TEST")
                .taskId(TASK_ID)
                .clanId(CLAN_ID)
                .recipientMemberId(MEMBER_RECIPIENT)
                .advanceAmount(new BigDecimal("2000000.00"))
                .receivedDate(Instant.now())
                .settlementDeadline(Instant.now().plus(5, ChronoUnit.DAYS))
                .createdByAccountId(ACCOUNT_BTC)
                .status(AdvanceVoucherStatus.ChoGiaoTien)
                .build());

        // BTC gọi disburse -> 403 Forbidden
        mockMvc.perform(post("/api/v1/advances/" + voucher.getAdvanceVoucherId() + "/disburse")
                        .with(authentication(auth(ACCOUNT_BTC, MEMBER_BTC, "MEMBER")))
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN_ID))
                .andExpect(status().isForbidden());
    }
}
