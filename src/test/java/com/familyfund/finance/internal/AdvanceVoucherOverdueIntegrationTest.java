package com.familyfund.finance.internal;

import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.entity.EventTask;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.finance.api.AdvanceVoucherOverdueService;
import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.api.dto.OverdueScanSummary;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End PostgreSQL Integration Test for US-48:
 * Background job scanning advance vouchers past settlement deadline that remain in ChuaTatToan,
 * transitioning their status to QuaHan and dispatching simultaneous alerts to recipient and organizers.
 */
@SpringBootTest
@Transactional
class AdvanceVoucherOverdueIntegrationTest {

    private static final String CLAN_ID = "CLN_US48_IT";
    private static final String BRANCH_ID = "BR_US48_IT";
    private static final String EVENT_ID = "EVT_US48_IT";
    private static final String TASK_ID = "TSK_US48_IT";

    private static final String MEMBER_BTC = "MEM_US48_BTC";
    private static final String MEMBER_TREASURER = "MEM_US48_TREASURER";
    private static final String MEMBER_RECIPIENT = "MEM_US48_RECIPIENT";

    private static final String ACCOUNT_BTC = "ACC_US48_BTC";
    private static final String ACCOUNT_TREASURER = "ACC_US48_TREASURER";
    private static final String ACCOUNT_RECIPIENT = "ACC_US48_RECIPIENT";

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

    @Autowired
    private AdvanceVoucherOverdueService advanceVoucherOverdueService;

    @Autowired
    private EntityManager entityManager;

    private Instant testNow;

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN_ID);
        testNow = Instant.parse("2026-04-20T10:00:00Z");

        clanRepository.save(Clan.builder().clanId(CLAN_ID).clanName("Dòng họ US48 IT").build());

        FamilyBranch branch = branchRepository.save(FamilyBranch.builder()
                .branchId(BRANCH_ID).clanId(CLAN_ID).branchName("Chi Một US48").generationLevel(1).build());

        Member btcMember = memberRepository.save(Member.builder()
                .memberId(MEMBER_BTC).branch(branch).fullName("Trưởng Ban Tổ Chức US48")
                .gender("Male").generation(1).status("Living").build());
        Member treasurerMember = memberRepository.save(Member.builder()
                .memberId(MEMBER_TREASURER).branch(branch).fullName("Thủ Quỹ US48")
                .gender("Female").generation(1).status("Living").build());
        Member recipientMember = memberRepository.save(Member.builder()
                .memberId(MEMBER_RECIPIENT).branch(branch).fullName("Người Nhận Tạm Ứng US48")
                .gender("Male").generation(2).status("Living").build());

        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_BTC).memberId(btcMember.getMemberId())
                .phoneNumber("0933000001").passwordHash("hash").accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_TREASURER).memberId(treasurerMember.getMemberId())
                .phoneNumber("0933000002").passwordHash("hash").accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT_RECIPIENT).memberId(recipientMember.getMemberId())
                .phoneNumber("0933000003").passwordHash("hash").accountStatus(AccountStatus.Active).build());

        Instant eventDate = testNow.plus(5, ChronoUnit.DAYS);
        eventRepository.save(Event.builder()
                .eventId(EVENT_ID).clanId(CLAN_ID).eventName("Đại Lễ Giỗ Họ US48")
                .eventDatetime(eventDate).location("Từ đường họ Nguyễn")
                .createdBy(ACCOUNT_BTC).status(EventStatus.RegistrationOpen).build());

        eventTaskRepository.save(EventTask.builder()
                .taskId(TASK_ID).eventId(EVENT_ID).taskName("Đặt 20 mâm cỗ")
                .advanceBudget(new BigDecimal("15000000.00")).deadline(eventDate.minus(1, ChronoUnit.DAYS))
                .status(com.familyfund.event.api.dto.TaskStatus.InProgress)
                .assignedMemberId(MEMBER_RECIPIENT).createdBy(ACCOUNT_BTC).build());

        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVENT_ID).memberId(MEMBER_BTC).eventRole(EventRole.HeadOfOrganizer).isReadOnly(false).build());
        eventPersonnelRepository.save(EventPersonnel.builder()
                .eventId(EVENT_ID).memberId(MEMBER_TREASURER).eventRole(EventRole.EventTreasurer).isReadOnly(false).build());

        // 1. Phiếu quá hạn (ChuaTatToan + deadline quá hạn 3 ngày) -> CẦN CHUYỂN SANG QuaHan
        advanceVoucherRepository.save(AdvanceVoucher.builder()
                .advanceVoucherId("ADV_US48_OVERDUE")
                .taskId(TASK_ID)
                .clanId(CLAN_ID)
                .recipientMemberId(MEMBER_RECIPIENT)
                .advanceAmount(new BigDecimal("6000000.00"))
                .receivedDate(testNow.minus(10, ChronoUnit.DAYS))
                .settlementDeadline(testNow.minus(3, ChronoUnit.DAYS))
                .createdByAccountId(ACCOUNT_BTC)
                .treasurerAccountId(ACCOUNT_TREASURER)
                .disbursedAt(testNow.minus(10, ChronoUnit.DAYS))
                .status(AdvanceVoucherStatus.ChuaTatToan)
                .build());

        // 2. Phiếu chưa giao tiền (ChoGiaoTien + deadline quá hạn) -> KHÔNG CHUYỂN SANG QuaHan (chưa nhận tiền)
        advanceVoucherRepository.save(AdvanceVoucher.builder()
                .advanceVoucherId("ADV_US48_WAITING")
                .taskId(TASK_ID)
                .clanId(CLAN_ID)
                .recipientMemberId(MEMBER_RECIPIENT)
                .advanceAmount(new BigDecimal("2000000.00"))
                .receivedDate(testNow.minus(5, ChronoUnit.DAYS))
                .settlementDeadline(testNow.minus(1, ChronoUnit.DAYS))
                .createdByAccountId(ACCOUNT_BTC)
                .status(AdvanceVoucherStatus.ChoGiaoTien)
                .build());

        // 3. Phiếu chưa đến hạn (ChuaTatToan + deadline trong tương lai) -> KHÔNG CHUYỂN SANG QuaHan
        advanceVoucherRepository.save(AdvanceVoucher.builder()
                .advanceVoucherId("ADV_US48_FUTURE")
                .taskId(TASK_ID)
                .clanId(CLAN_ID)
                .recipientMemberId(MEMBER_RECIPIENT)
                .advanceAmount(new BigDecimal("3000000.00"))
                .receivedDate(testNow.minus(2, ChronoUnit.DAYS))
                .settlementDeadline(testNow.plus(4, ChronoUnit.DAYS))
                .createdByAccountId(ACCOUNT_BTC)
                .treasurerAccountId(ACCOUNT_TREASURER)
                .disbursedAt(testNow.minus(2, ChronoUnit.DAYS))
                .status(AdvanceVoucherStatus.ChuaTatToan)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("US-48 E2E: Quét phiếu quá hạn -> đổi trạng thái sang QuaHan -> gửi cảnh báo đồng thời cho người nhận và BTC")
    void scanAndProcessOverdueVouchers_e2eFlow() {
        // 1. Thực thi lượt quét hạn chót với mốc testNow
        OverdueScanSummary summary = advanceVoucherOverdueService.scanAndProcessOverdueVouchers(testNow);

        assertThat(summary.scannedCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.overdueTransitionedCount()).isEqualTo(1);
        assertThat(summary.notificationsSentCount()).isGreaterThanOrEqualTo(2);

        // 2. Kiểm tra database trạng thái các phiếu
        AdvanceVoucher overdueVoucher = advanceVoucherRepository.findById("ADV_US48_OVERDUE").orElseThrow();
        assertThat(overdueVoucher.getStatus()).isEqualTo(AdvanceVoucherStatus.QuaHan);
        assertThat(overdueVoucher.getOverdueAt()).isNotNull();

        AdvanceVoucher waitingVoucher = advanceVoucherRepository.findById("ADV_US48_WAITING").orElseThrow();
        assertThat(waitingVoucher.getStatus()).isEqualTo(AdvanceVoucherStatus.ChoGiaoTien);
        assertThat(waitingVoucher.getOverdueAt()).isNull();

        AdvanceVoucher futureVoucher = advanceVoucherRepository.findById("ADV_US48_FUTURE").orElseThrow();
        assertThat(futureVoucher.getStatus()).isEqualTo(AdvanceVoucherStatus.ChuaTatToan);
        assertThat(futureVoucher.getOverdueAt()).isNull();

        // 3. Kiểm tra thông báo cảnh báo đã được ghi nhận trong bảng notifications (US-39)
        Number recipientNotifCount = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM notifications
                WHERE recipient_account_id = :accountId
                  AND category = 'Finance'
                  AND reference_id = 'ADV_US48_OVERDUE'
                """)
                .setParameter("accountId", ACCOUNT_RECIPIENT)
                .getSingleResult();
        assertThat(recipientNotifCount.intValue()).isEqualTo(1);

        Number btcNotifCount = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM notifications
                WHERE recipient_account_id = :accountId
                  AND category = 'Finance'
                  AND reference_id = 'ADV_US48_OVERDUE'
                """)
                .setParameter("accountId", ACCOUNT_BTC)
                .getSingleResult();
        assertThat(btcNotifCount.intValue()).isGreaterThanOrEqualTo(1);

        Number treasurerNotifCount = (Number) entityManager.createNativeQuery("""
                SELECT count(*) FROM notifications
                WHERE recipient_account_id = :accountId
                  AND category = 'Finance'
                  AND reference_id = 'ADV_US48_OVERDUE'
                """)
                .setParameter("accountId", ACCOUNT_TREASURER)
                .getSingleResult();
        assertThat(treasurerNotifCount.intValue()).isGreaterThanOrEqualTo(1);

        // 4. Kiểm tra tính Idempotent: chạy lại lượt quét ngay sau đó không chuyển đổi trùng hay gửi lặp
        OverdueScanSummary secondSummary = advanceVoucherOverdueService.scanAndProcessOverdueVouchers(testNow);
        assertThat(secondSummary.overdueTransitionedCount()).isZero();
    }
}
