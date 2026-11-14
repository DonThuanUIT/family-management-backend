package com.familyfund.notification.internal.listener;

import com.familyfund.event.api.dto.AssignableRoleCode;
import com.familyfund.event.api.dto.RoleActionType;
import com.familyfund.event.api.event.EventOpenedDomainEvent;
import com.familyfund.event.api.event.EventScheduleChangedDomainEvent;
import com.familyfund.event.api.event.RoleChangedDomainEvent;
import com.familyfund.event.api.event.TaskAssignedDomainEvent;
import com.familyfund.event.api.event.TaskProgressAlertIssuedDomainEvent;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link NotificationInboxListener} (US-39): which account receives which category for each
 * {@code event::api} domain event.
 */
@ExtendWith(MockitoExtension.class)
class NotificationInboxListenerTest {

    private static final String CLAN = "CLAN_NTF_LST";
    private static final String EVENT_ID = "EVT_LST_01";
    private static final String TASK_ID = "TSK_LST_01";
    private static final String BRANCH_ID = "BR_LST_01";

    @Mock
    private NotificationService notificationService;
    @Mock
    private IdentityRoleCommandService identityRoleCommandService;
    @Mock
    private GenealogyQueryService genealogyQueryService;

    @InjectMocks
    private NotificationInboxListener listener;

    private AccountSummaryDto account(String accountId, String memberId) {
        return new AccountSummaryDto(accountId, memberId, "0900000000", null, "Active", Set.of());
    }

    private List<NotificationCommand> capturedCommands(int expected) {
        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationService, times(expected)).notify(captor.capture());
        return captor.getAllValues();
    }

    // ------------------------------------------------------------------ events

    @Test
    @DisplayName("an opened event notifies every active account of the clan in the Event category")
    void eventOpenedFansOutToClan() {
        when(identityRoleCommandService.findActiveAccountsByClan(CLAN))
                .thenReturn(List.of(account("ACC_1", "MEM_1"), account("ACC_2", "MEM_2")));

        listener.onEventOpened(new EventOpenedDomainEvent(EVENT_ID, CLAN, "Giỗ tổ",
                Instant.parse("2026-04-20T03:00:00Z"), "10/03", "Nhà thờ họ", "ACC_HEAD"));

        List<NotificationCommand> commands = capturedCommands(2);
        assertThat(commands).extracting(NotificationCommand::recipientAccountId)
                .containsExactly("ACC_1", "ACC_2");
        assertThat(commands).allSatisfy(command -> {
            assertThat(command.category()).isEqualTo(NotificationCategory.Event);
            assertThat(command.clanId()).isEqualTo(CLAN);
            assertThat(command.referenceType()).isEqualTo("EVENT");
            assertThat(command.referenceId()).isEqualTo(EVENT_ID);
            assertThat(command.title()).isEqualTo("Sự kiện mới: Giỗ tổ");
            assertThat(command.content()).contains("Giỗ tổ").contains("Nhà thờ họ");
        });
    }

    @Test
    @DisplayName("an opened event in a clan without accounts produces no notification")
    void eventOpenedWithoutAccountsDoesNothing() {
        when(identityRoleCommandService.findActiveAccountsByClan(CLAN)).thenReturn(List.of());

        listener.onEventOpened(new EventOpenedDomainEvent(EVENT_ID, CLAN, "Giỗ tổ", null, null, null, "ACC_HEAD"));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("an event with a blank clan id is ignored")
    void eventOpenedWithoutClanIsIgnored() {
        listener.onEventOpened(new EventOpenedDomainEvent(EVENT_ID, "  ", "Giỗ tổ", null, null, null, null));

        verifyNoInteractions(notificationService, identityRoleCommandService);
    }

    @Test
    @DisplayName("a schedule change forwards the change summary to the whole clan")
    void scheduleChangedFansOutContentChanges() {
        when(identityRoleCommandService.findActiveAccountsByClan(CLAN))
                .thenReturn(List.of(account("ACC_1", "MEM_1")));

        listener.onEventScheduleChanged(new EventScheduleChangedDomainEvent(
                EVENT_ID, CLAN, "Giỗ tổ", "Đổi giờ", "Lịch trình dời sang 08:00", "ACC_HEAD"));

        NotificationCommand command = capturedCommands(1).getFirst();
        assertThat(command.category()).isEqualTo(NotificationCategory.Event);
        assertThat(command.title()).isEqualTo("Thay đổi lịch trình: Giỗ tổ");
        assertThat(command.content()).isEqualTo("Lịch trình dời sang 08:00");
    }

    @Test
    @DisplayName("a schedule change without a summary still sends a generic body")
    void scheduleChangedWithoutSummaryUsesFallbackBody() {
        when(identityRoleCommandService.findActiveAccountsByClan(CLAN))
                .thenReturn(List.of(account("ACC_1", "MEM_1")));

        listener.onEventScheduleChanged(new EventScheduleChangedDomainEvent(
                EVENT_ID, CLAN, "Giỗ tổ", null, "  ", null));

        assertThat(capturedCommands(1).getFirst().content())
                .isEqualTo("Sự kiện 'Giỗ tổ' vừa được cập nhật lịch trình.");
    }

    // ------------------------------------------------------------------ tasks

    @Test
    @DisplayName("a task assignment notifies the assignee and the assigned branch, without duplicates")
    void taskAssignedNotifiesAssigneeAndBranch() {
        when(identityRoleCommandService.findAccountByMemberId("MEM_ASSIGNEE"))
                .thenReturn(Optional.of(account("ACC_ASSIGNEE", "MEM_ASSIGNEE")));
        when(genealogyQueryService.getMemberIdsByBranch(CLAN, BRANCH_ID))
                .thenReturn(List.of("MEM_ASSIGNEE", "MEM_OTHER"));
        when(identityRoleCommandService.findAccountsByMemberIds(List.of("MEM_ASSIGNEE", "MEM_OTHER")))
                .thenReturn(Map.of(
                        "MEM_ASSIGNEE", account("ACC_ASSIGNEE", "MEM_ASSIGNEE"),
                        "MEM_OTHER", account("ACC_OTHER", "MEM_OTHER")));

        listener.onTaskAssigned(new TaskAssignedDomainEvent(EVENT_ID, CLAN, "Giỗ tổ", TASK_ID,
                "Chuẩn bị mâm cỗ", "MEM_ASSIGNEE", BRANCH_ID, new BigDecimal("3500000.00"),
                Instant.parse("2026-04-20T03:00:00Z"), "ACC_HEAD"));

        List<NotificationCommand> commands = capturedCommands(2);
        assertThat(commands).extracting(NotificationCommand::recipientAccountId)
                .containsExactly("ACC_ASSIGNEE", "ACC_OTHER");
        assertThat(commands).allSatisfy(command -> {
            assertThat(command.category()).isEqualTo(NotificationCategory.Task);
            assertThat(command.referenceType()).isEqualTo("TASK");
            assertThat(command.referenceId()).isEqualTo(TASK_ID);
            assertThat(command.title()).isEqualTo("Bạn được giao việc: Chuẩn bị mâm cỗ");
            assertThat(command.content()).contains("Giỗ tổ").contains("Chuẩn bị mâm cỗ").contains("20/04/2026");
        });
    }

    @Test
    @DisplayName("a task assignment to members without accounts produces no notification")
    void taskAssignedWithoutAccountsDoesNothing() {
        when(identityRoleCommandService.findAccountByMemberId("MEM_ASSIGNEE")).thenReturn(Optional.empty());
        when(genealogyQueryService.getMemberIdsByBranch(CLAN, BRANCH_ID)).thenReturn(List.of());

        listener.onTaskAssigned(new TaskAssignedDomainEvent(EVENT_ID, CLAN, "Giỗ tổ", TASK_ID,
                "Chuẩn bị mâm cỗ", "MEM_ASSIGNEE", BRANCH_ID, null, null, "ACC_HEAD"));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("a progress alert reuses the title/content rendered by the issuer")
    void taskProgressAlertReusesIssuerText() {
        listener.onTaskProgressAlertIssued(new TaskProgressAlertIssuedDomainEvent(
                CLAN, EVENT_ID, TASK_ID, "Chuẩn bị mâm cỗ", "Reminder24H",
                "Cảnh báo tiến độ: Chuẩn bị mâm cỗ",
                "Công việc còn 24 giờ, phụ trách: Nguyễn Văn Em",
                "MEM_ASSIGNEE", "ACC_ASSIGNEE"));

        NotificationCommand command = capturedCommands(1).getFirst();
        assertThat(command.recipientAccountId()).isEqualTo("ACC_ASSIGNEE");
        assertThat(command.category()).isEqualTo(NotificationCategory.Task);
        assertThat(command.title()).isEqualTo("Cảnh báo tiến độ: Chuẩn bị mâm cỗ");
        assertThat(command.content()).isEqualTo("Công việc còn 24 giờ, phụ trách: Nguyễn Văn Em");
        assertThat(command.referenceId()).isEqualTo(TASK_ID);
    }

    @Test
    @DisplayName("a progress alert without a recipient account is ignored")
    void taskProgressAlertWithoutAccountIsIgnored() {
        listener.onTaskProgressAlertIssued(new TaskProgressAlertIssuedDomainEvent(
                CLAN, EVENT_ID, TASK_ID, "Chuẩn bị mâm cỗ", "OverdueAlert12H",
                "title", "content", null, null));

        verifyNoInteractions(notificationService);
    }

    // ------------------------------------------------------------------ roles

    @Test
    @DisplayName("a role change notifies the target account directly")
    void roleChangedUsesTargetAccount() {
        listener.onRoleChanged(new RoleChangedDomainEvent("AUD_1", CLAN, "ACC_ACTOR", "MEM_TARGET",
                "ACC_TARGET", RoleActionType.ASSIGN, AssignableRoleCode.HeadOfOrganizer,
                null, "EVT_1", "Giỗ tổ", "desc", Instant.now()));

        NotificationCommand command = capturedCommands(1).getFirst();
        assertThat(command.recipientAccountId()).isEqualTo("ACC_TARGET");
        assertThat(command.category()).isEqualTo(NotificationCategory.Event);
        assertThat(command.referenceType()).isEqualTo("ROLE");
        assertThat(command.content()).contains("HeadOfOrganizer").contains("Giỗ tổ");
    }

    @Test
    @DisplayName("a role change without a target account falls back to the member lookup")
    void roleChangedFallsBackToMember() {
        when(identityRoleCommandService.findAccountByMemberId("MEM_TARGET"))
                .thenReturn(Optional.of(account("ACC_FROM_MEMBER", "MEM_TARGET")));

        listener.onRoleChanged(new RoleChangedDomainEvent("AUD_2", CLAN, "ACC_ACTOR", "MEM_TARGET",
                null, RoleActionType.REVOKE, AssignableRoleCode.ROLE_BRANCH_HEAD,
                null, null, null, null, Instant.now()));

        NotificationCommand command = capturedCommands(1).getFirst();
        assertThat(command.recipientAccountId()).isEqualTo("ACC_FROM_MEMBER");
        assertThat(command.content()).contains("bị thu hồi");
    }

    @Test
    @DisplayName("a role change for a member with no account is ignored")
    void roleChangedWithoutAccountIsIgnored() {
        when(identityRoleCommandService.findAccountByMemberId("MEM_TARGET")).thenReturn(Optional.empty());

        listener.onRoleChanged(new RoleChangedDomainEvent("AUD_3", CLAN, "ACC_ACTOR", "MEM_TARGET",
                null, RoleActionType.ASSIGN, AssignableRoleCode.OrganizerMember,
                null, null, null, null, Instant.now()));

        verifyNoInteractions(notificationService);
    }

    // ------------------------------------------------------------------ resilience

    @Test
    @DisplayName("a failure for one recipient does not stop the fan-out")
    void failureOnOneRecipientIsIsolated() {
        when(identityRoleCommandService.findActiveAccountsByClan(CLAN))
                .thenReturn(List.of(account("ACC_1", "MEM_1"), account("ACC_2", "MEM_2")));
        when(notificationService.notify(any(NotificationCommand.class)))
                .thenThrow(new IllegalStateException("db down"))
                .thenReturn("NTC_2");

        listener.onEventOpened(new EventOpenedDomainEvent(EVENT_ID, CLAN, "Giỗ tổ", null, null, null, "ACC_HEAD"));

        verify(notificationService, times(2)).notify(any(NotificationCommand.class));
    }
}
