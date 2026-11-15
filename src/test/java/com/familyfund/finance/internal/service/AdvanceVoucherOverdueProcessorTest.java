package com.familyfund.finance.internal.service;

import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventTaskSummaryDto;
import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.internal.dto.OverdueProcessingResult;
import com.familyfund.finance.internal.entity.AdvanceVoucher;
import com.familyfund.finance.internal.repository.AdvanceVoucherRepository;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvanceVoucherOverdueProcessorTest {

    @Mock
    private AdvanceVoucherRepository advanceVoucherRepository;

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private IdentityRoleCommandService identityRoleCommandService;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AdvanceVoucherOverdueProcessor processor;

    private Instant now;
    private AdvanceVoucher voucher;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-04-15T12:00:00Z");
        voucher = AdvanceVoucher.builder()
                .advanceVoucherId("ADV_OVERDUE_01")
                .taskId("TSK_01")
                .clanId("CLAN_01")
                .recipientMemberId("MEM_RECIPIENT")
                .advanceAmount(new BigDecimal("5000000.00"))
                .receivedDate(now.minus(10, ChronoUnit.DAYS))
                .settlementDeadline(now.minus(2, ChronoUnit.DAYS))
                .createdByAccountId("ACC_CREATOR")
                .treasurerAccountId("ACC_TREASURER")
                .status(AdvanceVoucherStatus.ChuaTatToan)
                .build();
    }

    @Test
    @DisplayName("US-48: Phiếu chưa quyết toán quá hạn chuyển sang QuaHan và gửi cảnh báo cho người nhận và BTC")
    void processOverdueVoucher_success() {
        when(advanceVoucherRepository.findById("ADV_OVERDUE_01")).thenReturn(Optional.of(voucher));

        EventTaskSummaryDto taskSummary = new EventTaskSummaryDto(
                "TSK_01", "EVT_01", "CLAN_01", "Mua cỗ bàn",
                new BigDecimal("10000000.00"), now.plus(1, ChronoUnit.DAYS),
                TaskStatus.InProgress, "MEM_RECIPIENT", null
        );
        when(eventQueryService.findTaskSummary("TSK_01")).thenReturn(Optional.of(taskSummary));

        when(genealogyQueryService.findMemberSummary("MEM_RECIPIENT"))
                .thenReturn(Optional.of(new MemberSummaryDto("MEM_RECIPIENT", "Nguyễn Văn Nhận", "CLAN_01", "BR_01")));

        AccountSummaryDto recipientAccount = new AccountSummaryDto("ACC_RECIPIENT", "MEM_RECIPIENT", "0900000001", "rec@fund.vn", "Active", Set.of("ROLE_MEMBER"));
        when(identityRoleCommandService.findAccountByMemberId("MEM_RECIPIENT")).thenReturn(Optional.of(recipientAccount));

        // BTC: Trưởng ban tổ chức MEM_BTC_HEAD
        when(eventQueryService.findOrganizerMemberIds("EVT_01")).thenReturn(Set.of("MEM_BTC_HEAD"));
        AccountSummaryDto btcAccount = new AccountSummaryDto("ACC_BTC_HEAD", "MEM_BTC_HEAD", "0900000002", "btc@fund.vn", "Active", Set.of("ROLE_MEMBER"));
        when(identityRoleCommandService.findAccountsByMemberIds(Set.of("MEM_BTC_HEAD"))).thenReturn(Map.of("MEM_BTC_HEAD", btcAccount));

        OverdueProcessingResult result = processor.processOverdueVoucher("ADV_OVERDUE_01", now);

        assertThat(result.transitioned()).isTrue();
        assertThat(voucher.getStatus()).isEqualTo(AdvanceVoucherStatus.QuaHan);
        assertThat(voucher.getOverdueAt()).isEqualTo(now);
        verify(advanceVoucherRepository).save(voucher);

        // Notifications: 1 recipient + 3 BTC/organizers (ACC_BTC_HEAD, ACC_CREATOR, ACC_TREASURER)
        assertThat(result.notificationsSent()).isEqualTo(4);

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService, times(4)).notify(captor.capture());

        List<NotificationCommand> commands = captor.getAllValues();

        // 1. Recipient notification
        NotificationCommand recipientCmd = commands.stream()
                .filter(c -> c.recipientAccountId().equals("ACC_RECIPIENT"))
                .findFirst().orElseThrow();
        assertThat(recipientCmd.category()).isEqualTo(NotificationCategory.Finance);
        assertThat(recipientCmd.title()).contains("Quá hạn quyết toán tạm ứng");
        assertThat(recipientCmd.content()).contains("ADV_OVERDUE_01");
        assertThat(recipientCmd.content()).contains("5.000.000");
        assertThat(recipientCmd.content()).contains("Mua cỗ bàn");
        assertThat(recipientCmd.referenceType()).isEqualTo("ADVANCE_VOUCHER");

        // 2. Organizer notifications
        Set<String> recipientIds = commands.stream().map(NotificationCommand::recipientAccountId).collect(java.util.stream.Collectors.toSet());
        assertThat(recipientIds).contains("ACC_RECIPIENT", "ACC_BTC_HEAD", "ACC_CREATOR", "ACC_TREASURER");
    }

    @Test
    @DisplayName("US-48: Phiếu không ở trạng thái ChuaTatToan (e.g. ChoGiaoTien) sẽ bị bỏ qua")
    void processOverdueVoucher_notChuaTatToan_skipped() {
        voucher.setStatus(AdvanceVoucherStatus.ChoGiaoTien);
        when(advanceVoucherRepository.findById("ADV_OVERDUE_01")).thenReturn(Optional.of(voucher));

        OverdueProcessingResult result = processor.processOverdueVoucher("ADV_OVERDUE_01", now);

        assertThat(result.transitioned()).isFalse();
        assertThat(result.notificationsSent()).isZero();
        assertThat(voucher.getStatus()).isEqualTo(AdvanceVoucherStatus.ChoGiaoTien);
        verify(advanceVoucherRepository, never()).save(any());
        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("US-48: Phiếu chưa đến hạn settlement_deadline sẽ bị bỏ qua")
    void processOverdueVoucher_deadlineNotPassed_skipped() {
        voucher.setSettlementDeadline(now.plus(5, ChronoUnit.DAYS));
        when(advanceVoucherRepository.findById("ADV_OVERDUE_01")).thenReturn(Optional.of(voucher));

        OverdueProcessingResult result = processor.processOverdueVoucher("ADV_OVERDUE_01", now);

        assertThat(result.transitioned()).isFalse();
        assertThat(result.notificationsSent()).isZero();
        verify(advanceVoucherRepository, never()).save(any());
        verify(notificationService, never()).notify(any());
    }

    @Test
    @DisplayName("US-48: Người nhận chưa có tài khoản người dùng -> cảnh báo người nhận bỏ qua nhưng BTC vẫn nhận cảnh báo")
    void processOverdueVoucher_recipientNoAccount_stillAlertsOrganizers() {
        when(advanceVoucherRepository.findById("ADV_OVERDUE_01")).thenReturn(Optional.of(voucher));
        when(eventQueryService.findTaskSummary("TSK_01")).thenReturn(Optional.empty());
        when(genealogyQueryService.findMemberSummary("MEM_RECIPIENT")).thenReturn(Optional.empty());

        // Recipient has no account
        when(identityRoleCommandService.findAccountByMemberId("MEM_RECIPIENT")).thenReturn(Optional.empty());

        OverdueProcessingResult result = processor.processOverdueVoucher("ADV_OVERDUE_01", now);

        assertThat(result.transitioned()).isTrue();
        assertThat(voucher.getStatus()).isEqualTo(AdvanceVoucherStatus.QuaHan);
        // Organizers: ACC_CREATOR + ACC_TREASURER
        assertThat(result.notificationsSent()).isEqualTo(2);
        verify(notificationService, times(2)).notify(any());
    }
}
