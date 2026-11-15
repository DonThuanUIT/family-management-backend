package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.event.api.dto.EventTaskSummaryDto;
import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.internal.dto.request.ConfirmDisbursementRequest;
import com.familyfund.finance.internal.dto.request.CreateAdvanceVoucherRequest;
import com.familyfund.finance.internal.dto.response.AdvanceVoucherResponse;
import com.familyfund.finance.internal.entity.AdvanceVoucher;
import com.familyfund.finance.internal.repository.AdvanceVoucherRepository;
import com.familyfund.finance.internal.util.AdvanceVoucherIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdvanceVoucherServiceImplTest {

    @Mock
    private AdvanceVoucherRepository advanceVoucherRepository;

    @Mock
    private AdvanceVoucherIdGenerator advanceVoucherIdGenerator;

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @Mock
    private IdentityRoleCommandService identityRoleCommandService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AdvanceVoucherServiceImpl service;

    private static final String CLAN_ID = "CLN_TEST";
    private static final String EVENT_ID = "EVT_01";
    private static final String TASK_ID = "TSK_01";
    private static final String RECIPIENT_MEMBER_ID = "MEM_01";
    private static final String CALLER_MEMBER_ID = "MEM_CALLER";
    private static final String CALLER_ACCOUNT_ID = "ACC_CALLER";

    private Instant now;
    private Instant receivedDate;
    private Instant deadline;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        now = Instant.now();
        receivedDate = now.plus(1, ChronoUnit.DAYS);
        deadline = now.plus(5, ChronoUnit.DAYS);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Lập phiếu tạm ứng thành công khi người gọi là Trưởng ban tổ chức (HeadOfOrganizer)")
    void createAdvanceVoucher_success_whenOrganizer() {
        CreateAdvanceVoucherRequest request = new CreateAdvanceVoucherRequest(
                null, TASK_ID, RECIPIENT_MEMBER_ID, new BigDecimal("5000000.00"),
                receivedDate, deadline, "Cam kết mua sắm đầy đủ hoá đơn"
        );

        EventTaskSummaryDto task = new EventTaskSummaryDto(
                TASK_ID, EVENT_ID, CLAN_ID, "Mua sắm cỗ bàn",
                new BigDecimal("10000000.00"), deadline.plus(2, ChronoUnit.DAYS),
                TaskStatus.InProgress, RECIPIENT_MEMBER_ID, null
        );

        MemberSummaryDto member = new MemberSummaryDto(RECIPIENT_MEMBER_ID, "Nguyễn Văn A", CLAN_ID, "Chi 1");

        when(eventQueryService.findTaskSummary(TASK_ID)).thenReturn(Optional.of(task));
        when(eventQueryService.isEventClosed(EVENT_ID)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(CALLER_MEMBER_ID));
        when(eventQueryService.getMemberRolesInEvent(EVENT_ID, CALLER_MEMBER_ID))
                .thenReturn(Set.of(EventRole.HeadOfOrganizer));
        when(genealogyQueryService.findMemberSummary(RECIPIENT_MEMBER_ID)).thenReturn(Optional.of(member));
        when(advanceVoucherIdGenerator.generate()).thenReturn("ADV12345678");
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(CALLER_ACCOUNT_ID));
        when(advanceVoucherRepository.save(any(AdvanceVoucher.class))).thenAnswer(i -> i.getArgument(0));

        AdvanceVoucherResponse response = service.createAdvanceVoucher(request);

        assertThat(response).isNotNull();
        assertThat(response.advanceVoucherId()).isEqualTo("ADV12345678");
        assertThat(response.status()).isEqualTo(AdvanceVoucherStatus.ChoGiaoTien);
        assertThat(response.advanceAmount()).isEqualByComparingTo("5000000.00");
        assertThat(response.recipientMemberId()).isEqualTo(RECIPIENT_MEMBER_ID);
        assertThat(response.recipientName()).isEqualTo("Nguyễn Văn A");

        verify(advanceVoucherRepository).save(any(AdvanceVoucher.class));
    }

    @Test
    @DisplayName("Lập phiếu tạm ứng thất bại khi deadline nộp chứng từ trước ngày nhận tiền")
    void createAdvanceVoucher_throws_whenDeadlineBeforeReceivedDate() {
        CreateAdvanceVoucherRequest request = new CreateAdvanceVoucherRequest(
                null, TASK_ID, RECIPIENT_MEMBER_ID, new BigDecimal("1000000.00"),
                deadline, receivedDate, "Cam kết"
        );

        EventTaskSummaryDto task = new EventTaskSummaryDto(
                TASK_ID, EVENT_ID, CLAN_ID, "Task",
                BigDecimal.ZERO, deadline.plus(2, ChronoUnit.DAYS),
                TaskStatus.InProgress, RECIPIENT_MEMBER_ID, null
        );

        MemberSummaryDto member = new MemberSummaryDto(RECIPIENT_MEMBER_ID, "Nguyễn Văn A", CLAN_ID, "Chi 1");

        when(eventQueryService.findTaskSummary(TASK_ID)).thenReturn(Optional.of(task));
        when(eventQueryService.isEventClosed(EVENT_ID)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);
        when(genealogyQueryService.findMemberSummary(RECIPIENT_MEMBER_ID)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.createAdvanceVoucher(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Hạn nộp chứng từ quyết toán phải sau ngày nhận tiền");
    }

    @Test
    @DisplayName("Lập phiếu tạm ứng thất bại khi sự kiện đã Closed")
    void createAdvanceVoucher_throws_whenEventClosed() {
        CreateAdvanceVoucherRequest request = new CreateAdvanceVoucherRequest(
                null, TASK_ID, RECIPIENT_MEMBER_ID, new BigDecimal("1000000.00"),
                receivedDate, deadline, "Cam kết"
        );

        EventTaskSummaryDto task = new EventTaskSummaryDto(
                TASK_ID, EVENT_ID, CLAN_ID, "Task",
                BigDecimal.ZERO, deadline, TaskStatus.InProgress, RECIPIENT_MEMBER_ID, null
        );

        when(eventQueryService.findTaskSummary(TASK_ID)).thenReturn(Optional.of(task));
        when(eventQueryService.isEventClosed(EVENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.createAdvanceVoucher(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("đã đóng (Closed)");
    }

    @Test
    @DisplayName("Lập phiếu tạm ứng thất bại khi người gọi không thuộc Ban tổ chức")
    void createAdvanceVoucher_throws_whenCallerNotBtc() {
        CreateAdvanceVoucherRequest request = new CreateAdvanceVoucherRequest(
                null, TASK_ID, RECIPIENT_MEMBER_ID, new BigDecimal("1000000.00"),
                receivedDate, deadline, "Cam kết"
        );

        EventTaskSummaryDto task = new EventTaskSummaryDto(
                TASK_ID, EVENT_ID, CLAN_ID, "Task",
                BigDecimal.ZERO, deadline, TaskStatus.InProgress, RECIPIENT_MEMBER_ID, null
        );

        when(eventQueryService.findTaskSummary(TASK_ID)).thenReturn(Optional.of(task));
        when(eventQueryService.isEventClosed(EVENT_ID)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(CALLER_MEMBER_ID));
        when(eventQueryService.getMemberRolesInEvent(EVENT_ID, CALLER_MEMBER_ID)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.createAdvanceVoucher(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ Ban tổ chức sự kiện mới có quyền lập phiếu");
    }

    @Test
    @DisplayName("Thủ quỹ xác nhận đã giao tiền chuyển trạng thái thành ChuaTatToan thành công")
    void confirmDisbursement_success_whenTreasurer() {
        AdvanceVoucher existing = AdvanceVoucher.builder()
                .advanceVoucherId("ADV_001")
                .taskId(TASK_ID)
                .clanId(CLAN_ID)
                .recipientMemberId(RECIPIENT_MEMBER_ID)
                .advanceAmount(new BigDecimal("3000000.00"))
                .receivedDate(receivedDate)
                .settlementDeadline(deadline)
                .status(AdvanceVoucherStatus.ChoGiaoTien)
                .createdByAccountId("ACC_BTC")
                .build();

        EventTaskSummaryDto task = new EventTaskSummaryDto(
                TASK_ID, EVENT_ID, CLAN_ID, "Thuê âm thanh",
                BigDecimal.ZERO, deadline, TaskStatus.InProgress, RECIPIENT_MEMBER_ID, null
        );

        when(advanceVoucherRepository.findById("ADV_001")).thenReturn(Optional.of(existing));
        when(eventQueryService.findTaskSummary(TASK_ID)).thenReturn(Optional.of(task));
        when(eventQueryService.isEventClosed(EVENT_ID)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_TREASURER"));
        when(eventQueryService.getMemberRolesInEvent(EVENT_ID, "MEM_TREASURER"))
                .thenReturn(Set.of(EventRole.EventTreasurer));
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of("ACC_TREASURER"));
        when(advanceVoucherRepository.save(any(AdvanceVoucher.class))).thenAnswer(i -> i.getArgument(0));

        ConfirmDisbursementRequest confirmRequest = new ConfirmDisbursementRequest("Đã giao tiền mặt tại nhà thờ họ", now);
        AdvanceVoucherResponse response = service.confirmDisbursement("ADV_001", confirmRequest);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(AdvanceVoucherStatus.ChuaTatToan);
        assertThat(response.treasurerAccountId()).isEqualTo("ACC_TREASURER");
        assertThat(response.disbursedAt()).isEqualTo(now);
        assertThat(response.disbursementNotes()).isEqualTo("Đã giao tiền mặt tại nhà thờ họ");

        verify(advanceVoucherRepository).save(existing);
    }

    @Test
    @DisplayName("Thủ quỹ xác nhận giao tiền thất bại khi phiếu đã ở trạng thái ChuaTatToan")
    void confirmDisbursement_throws_whenAlreadyDisbursed() {
        AdvanceVoucher existing = AdvanceVoucher.builder()
                .advanceVoucherId("ADV_001")
                .taskId(TASK_ID)
                .clanId(CLAN_ID)
                .recipientMemberId(RECIPIENT_MEMBER_ID)
                .advanceAmount(new BigDecimal("3000000.00"))
                .status(AdvanceVoucherStatus.ChuaTatToan)
                .build();

        EventTaskSummaryDto task = new EventTaskSummaryDto(
                TASK_ID, EVENT_ID, CLAN_ID, "Task",
                BigDecimal.ZERO, deadline, TaskStatus.InProgress, RECIPIENT_MEMBER_ID, null
        );

        when(advanceVoucherRepository.findById("ADV_001")).thenReturn(Optional.of(existing));
        when(eventQueryService.findTaskSummary(TASK_ID)).thenReturn(Optional.of(task));
        when(eventQueryService.isEventClosed(EVENT_ID)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> service.confirmDisbursement("ADV_001", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ChuaTatToan");
    }

    @Test
    @DisplayName("Thao tác xác nhận giao tiền thất bại khi người gọi không phải Thủ quỹ (EventTreasurer)")
    void confirmDisbursement_throws_whenNotTreasurer() {
        AdvanceVoucher existing = AdvanceVoucher.builder()
                .advanceVoucherId("ADV_001")
                .taskId(TASK_ID)
                .clanId(CLAN_ID)
                .recipientMemberId(RECIPIENT_MEMBER_ID)
                .status(AdvanceVoucherStatus.ChoGiaoTien)
                .build();

        EventTaskSummaryDto task = new EventTaskSummaryDto(
                TASK_ID, EVENT_ID, CLAN_ID, "Task",
                BigDecimal.ZERO, deadline, TaskStatus.InProgress, RECIPIENT_MEMBER_ID, null
        );

        when(advanceVoucherRepository.findById("ADV_001")).thenReturn(Optional.of(existing));
        when(eventQueryService.findTaskSummary(TASK_ID)).thenReturn(Optional.of(task));
        when(eventQueryService.isEventClosed(EVENT_ID)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.hasAuthority(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(CALLER_MEMBER_ID));
        when(eventQueryService.getMemberRolesInEvent(EVENT_ID, CALLER_MEMBER_ID))
                .thenReturn(Set.of(EventRole.OrganizerMember)); // Not treasurer!

        assertThatThrownBy(() -> service.confirmDisbursement("ADV_001", null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ Thủ quỹ sự kiện mới có quyền");
    }
}
