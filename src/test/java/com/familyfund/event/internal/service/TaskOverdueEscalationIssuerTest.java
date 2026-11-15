package com.familyfund.event.internal.service;

import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.TaskAlertLevel;
import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventTask;
import com.familyfund.event.internal.entity.TaskProgressAlert;
import com.familyfund.event.internal.notification.NotificationSender;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.event.internal.repository.TaskProgressAlertRepository;
import com.familyfund.event.internal.util.AlertIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.BranchSummaryDto;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the US-47 12-hour overdue escalation: tasks that went past their deadline by 12 hours or
 * more escalate to the representative of the responsible branch (the assignee's manager), exactly once,
 * naming the task, the person in charge and how many hours it is late.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskOverdueEscalationIssuer Unit Tests")
class TaskOverdueEscalationIssuerTest {

    private static final String CLAN_ID = "CLAN_TEST";
    private static final String EVENT_ID = "EVT_TEST_001";
    private static final String TASK_ID = "TSK_TEST_001";
    private static final String BRANCH_ID = "BR_TEST_001";
    private static final String BRANCH_NAME = "Chi Mot";
    private static final String ASSIGNEE_MEMBER_ID = "MEM_ASSIGNEE_001";
    private static final String ASSIGNEE_NAME = "Nguyen Van Em";
    private static final String REP_MEMBER_ID = "MEM_REP_001";
    private static final String REP_NAME = "Nguyen Van Truong Chi";
    private static final String REP_ACCOUNT_ID = "ACC_REP_001";
    private static final String ALERT_ID = "PWA_TEST_001";
    private static final Instant NOW = Instant.parse("2026-04-20T09:00:00Z");

    @Mock
    private EventTaskRepository eventTaskRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TaskProgressAlertRepository taskProgressAlertRepository;
    @Mock
    private AlertIdGenerator alertIdGenerator;
    @Mock
    private GenealogyQueryService genealogyQueryService;
    @Mock
    private IdentityRoleCommandService identityRoleCommandService;
    @Mock
    private NotificationSender notificationSender;

    @Mock
    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private TaskOverdueEscalationIssuer issuer;

    private EventTask task(Instant deadline, TaskStatus status, String branchId, String memberId) {
        return EventTask.builder()
                .taskId(TASK_ID)
                .eventId(EVENT_ID)
                .taskName("Mua sắm hoa quả, trầu cau")
                .advanceBudget(new BigDecimal("3500000.00"))
                .deadline(deadline)
                .status(status)
                .assignedBranchId(branchId)
                .assignedMemberId(memberId)
                .createdBy("ACC_CREATOR")
                .build();
    }

    private EventTask overdueTask(Duration overdueBy, TaskStatus status, String branchId, String memberId) {
        return task(NOW.minus(overdueBy), status, branchId, memberId);
    }

    private Event event(EventStatus status) {
        return Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Giỗ Tổ 2026")
                .eventDatetime(Instant.parse("2026-06-01T00:00:00Z"))
                .location("Từ đường dòng họ")
                .status(status)
                .createdBy("ACC_CREATOR")
                .build();
    }

    private BranchSummaryDto branch(String representativeMemberId) {
        return new BranchSummaryDto(BRANCH_ID, CLAN_ID, BRANCH_NAME, 2, representativeMemberId, REP_NAME);
    }

    private void stubHappyPath(EventTask task, EventStatus eventStatus) {
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event(eventStatus)));
        when(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_ID, TaskAlertLevel.OverdueAlert12H))
                .thenReturn(false);
        when(alertIdGenerator.generate()).thenReturn(ALERT_ID);
        when(genealogyQueryService.findBranchByIdAndClanId(BRANCH_ID, CLAN_ID))
                .thenReturn(Optional.of(branch(REP_MEMBER_ID)));
        when(genealogyQueryService.findMemberSummary(ASSIGNEE_MEMBER_ID))
                .thenReturn(Optional.of(new MemberSummaryDto(ASSIGNEE_MEMBER_ID, ASSIGNEE_NAME, CLAN_ID, BRANCH_ID)));
        when(identityRoleCommandService.findAccountByMemberId(REP_MEMBER_ID)).thenReturn(Optional.of(
                new AccountSummaryDto(REP_ACCOUNT_ID, REP_MEMBER_ID, "0900000001", null, "Active",
                        Set.of("ROLE_BRANCH_HEAD"))));
    }

    @Test
    @DisplayName("Escalates a 15h-overdue task to the branch representative with task, person in charge and lateness")
    void escalatesToBranchRepresentative() {
        EventTask task = overdueTask(Duration.ofHours(15), TaskStatus.InProgress, BRANCH_ID, ASSIGNEE_MEMBER_ID);
        stubHappyPath(task, EventStatus.Ongoing);

        assertTrue(issuer.issueOverdueEscalation(TASK_ID, NOW));

        ArgumentCaptor<TaskProgressAlert> captor = ArgumentCaptor.forClass(TaskProgressAlert.class);
        verify(taskProgressAlertRepository).save(captor.capture());
        TaskProgressAlert saved = captor.getValue();

        assertEquals(ALERT_ID, saved.getAlertId());
        assertEquals(TASK_ID, saved.getTaskId());
        assertEquals(TaskAlertLevel.OverdueAlert12H, saved.getAlertLevel());
        // The recipient is the branch representative, not the assignee.
        assertEquals(REP_ACCOUNT_ID, saved.getRecipientAccountId());
        assertEquals(NOW, saved.getSentTime());
        assertFalse(saved.getIsRead());
        assertTrue(saved.getAlertContent().contains("Mua sắm hoa quả, trầu cau"));
        assertTrue(saved.getAlertContent().contains("Giỗ Tổ 2026"));
        assertTrue(saved.getAlertContent().contains("15 giờ 0 phút"));
        assertTrue(saved.getAlertContent().contains(ASSIGNEE_NAME));
        assertTrue(saved.getAlertContent().contains(ASSIGNEE_MEMBER_ID));
        assertTrue(saved.getAlertContent().contains(BRANCH_NAME));
        assertTrue(saved.getAlertContent().contains(REP_MEMBER_ID));

        verify(notificationSender).sendTaskProgressWarning(
                eq(CLAN_ID),
                eq(EVENT_ID),
                eq(TASK_ID),
                eq(TaskAlertLevel.OverdueAlert12H),
                any(String.class),
                eq(saved.getAlertContent()),
                eq(REP_MEMBER_ID),
                eq(REP_ACCOUNT_ID)
        );
    }

    @Test
    @DisplayName("Treats a deadline exactly 12 hours late as overdue and escalates")
    void escalatesAtExactBoundary() {
        EventTask task = overdueTask(TaskOverdueEscalationIssuer.ESCALATION_DELAY, TaskStatus.InProgress,
                BRANCH_ID, ASSIGNEE_MEMBER_ID);
        stubHappyPath(task, EventStatus.Ongoing);

        assertTrue(issuer.issueOverdueEscalation(TASK_ID, NOW));
        verify(taskProgressAlertRepository).save(any(TaskProgressAlert.class));
    }

    @Test
    @DisplayName("Skips a task that is 11h59m late (escalation threshold not reached)")
    void skipsTaskNotOverdueLongEnough() {
        EventTask task = overdueTask(Duration.ofHours(11).plusMinutes(59), TaskStatus.InProgress,
                BRANCH_ID, ASSIGNEE_MEMBER_ID);
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertFalse(issuer.issueOverdueEscalation(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips completed tasks even when they are long overdue")
    void skipsCompletedTask() {
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(
                overdueTask(Duration.ofDays(3), TaskStatus.Completed, BRANCH_ID, ASSIGNEE_MEMBER_ID)));

        assertFalse(issuer.issueOverdueEscalation(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Is idempotent: a task that already has an OverdueAlert12H log is skipped")
    void skipsAlreadyEscalatedTask() {
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(
                overdueTask(Duration.ofHours(20), TaskStatus.InProgress, BRANCH_ID, ASSIGNEE_MEMBER_ID)));
        when(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_ID, TaskAlertLevel.OverdueAlert12H))
                .thenReturn(true);

        assertFalse(issuer.issueOverdueEscalation(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips when the event is closed (read-only bookkeeping)")
    void skipsClosedEvent() {
        EventTask task = overdueTask(Duration.ofHours(30), TaskStatus.InProgress, BRANCH_ID, ASSIGNEE_MEMBER_ID);
        stubHappyPath(task, EventStatus.Closed);

        assertFalse(issuer.issueOverdueEscalation(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips tasks with neither an assigned branch nor an assigned member")
    void skipsTaskWithoutResponsibleParty() {
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(
                overdueTask(Duration.ofHours(30), TaskStatus.Unassigned, null, null)));

        assertFalse(issuer.issueOverdueEscalation(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips when the branch is not part of the event's clan")
    void skipsBranchOutsideClan() {
        EventTask task = overdueTask(Duration.ofHours(20), TaskStatus.InProgress, BRANCH_ID, ASSIGNEE_MEMBER_ID);
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event(EventStatus.Ongoing)));
        when(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_ID, TaskAlertLevel.OverdueAlert12H))
                .thenReturn(false);
        when(genealogyQueryService.findBranchByIdAndClanId(BRANCH_ID, CLAN_ID)).thenReturn(Optional.empty());

        assertFalse(issuer.issueOverdueEscalation(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips when the branch has no representative (nobody to escalate to)")
    void skipsBranchWithoutRepresentative() {
        EventTask task = overdueTask(Duration.ofHours(20), TaskStatus.InProgress, BRANCH_ID, ASSIGNEE_MEMBER_ID);
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event(EventStatus.Ongoing)));
        when(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_ID, TaskAlertLevel.OverdueAlert12H))
                .thenReturn(false);
        when(genealogyQueryService.findBranchByIdAndClanId(BRANCH_ID, CLAN_ID))
                .thenReturn(Optional.of(branch(null)));

        assertFalse(issuer.issueOverdueEscalation(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips when the representative has no linked user account (no delivery channel)")
    void skipsRepresentativeWithoutAccount() {
        EventTask task = overdueTask(Duration.ofHours(20), TaskStatus.InProgress, BRANCH_ID, ASSIGNEE_MEMBER_ID);
        stubHappyPath(task, EventStatus.Ongoing);
        when(identityRoleCommandService.findAccountByMemberId(REP_MEMBER_ID)).thenReturn(Optional.empty());

        assertFalse(issuer.issueOverdueEscalation(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Falls back to the assignee's home branch when the task has no assigned_branch_id")
    void fallsBackToAssigneeHomeBranch() {
        EventTask task = overdueTask(Duration.ofHours(18), TaskStatus.InProgress, null, ASSIGNEE_MEMBER_ID);
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event(EventStatus.Ongoing)));
        when(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_ID, TaskAlertLevel.OverdueAlert12H))
                .thenReturn(false);
        when(alertIdGenerator.generate()).thenReturn(ALERT_ID);
        when(genealogyQueryService.findMemberSummary(ASSIGNEE_MEMBER_ID))
                .thenReturn(Optional.of(new MemberSummaryDto(ASSIGNEE_MEMBER_ID, ASSIGNEE_NAME, CLAN_ID, BRANCH_ID)));
        when(genealogyQueryService.findBranchByIdAndClanId(BRANCH_ID, CLAN_ID))
                .thenReturn(Optional.of(branch(REP_MEMBER_ID)));
        when(identityRoleCommandService.findAccountByMemberId(REP_MEMBER_ID)).thenReturn(Optional.of(
                new AccountSummaryDto(REP_ACCOUNT_ID, REP_MEMBER_ID, "0900000001", null, "Active",
                        Set.of("ROLE_BRANCH_HEAD"))));

        assertTrue(issuer.issueOverdueEscalation(TASK_ID, NOW));

        verify(genealogyQueryService).findBranchByIdAndClanId(BRANCH_ID, CLAN_ID);
        verify(notificationSender).sendTaskProgressWarning(
                eq(CLAN_ID), eq(EVENT_ID), eq(TASK_ID), eq(TaskAlertLevel.OverdueAlert12H),
                anyString(), anyString(), eq(REP_MEMBER_ID), eq(REP_ACCOUNT_ID));
    }

    @Test
    @DisplayName("Still escalates a branch-only task and states that no individual is in charge")
    void escalatesBranchOnlyTask() {
        EventTask task = overdueTask(Duration.ofHours(13), TaskStatus.InProgress, BRANCH_ID, null);
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event(EventStatus.Ongoing)));
        when(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_ID, TaskAlertLevel.OverdueAlert12H))
                .thenReturn(false);
        when(alertIdGenerator.generate()).thenReturn(ALERT_ID);
        when(genealogyQueryService.findBranchByIdAndClanId(BRANCH_ID, CLAN_ID))
                .thenReturn(Optional.of(branch(REP_MEMBER_ID)));
        when(identityRoleCommandService.findAccountByMemberId(REP_MEMBER_ID)).thenReturn(Optional.of(
                new AccountSummaryDto(REP_ACCOUNT_ID, REP_MEMBER_ID, "0900000001", null, "Active",
                        Set.of("ROLE_BRANCH_HEAD"))));

        assertTrue(issuer.issueOverdueEscalation(TASK_ID, NOW));

        ArgumentCaptor<TaskProgressAlert> captor = ArgumentCaptor.forClass(TaskProgressAlert.class);
        verify(taskProgressAlertRepository).save(captor.capture());
        assertTrue(captor.getValue().getAlertContent().contains("Chưa phân công cá nhân"));
    }

    @Test
    @DisplayName("Skips unknown task ids and blank input safely")
    void skipsUnknownOrBlankTaskId() {
        when(eventTaskRepository.findById(anyString())).thenReturn(Optional.empty());

        assertFalse(issuer.issueOverdueEscalation("TSK_UNKNOWN", NOW));
        assertFalse(issuer.issueOverdueEscalation("  ", NOW));
        assertFalse(issuer.issueOverdueEscalation(null, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Truncates long task names so the notification title never exceeds 200 characters")
    void truncatesLongTitle() {
        EventTask task = task(NOW.minus(Duration.ofHours(20)), TaskStatus.InProgress, BRANCH_ID, ASSIGNEE_MEMBER_ID);
        task.setTaskName("A".repeat(250));
        stubHappyPath(task, EventStatus.Ongoing);

        assertTrue(issuer.issueOverdueEscalation(TASK_ID, NOW));

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).sendTaskProgressWarning(
                eq(CLAN_ID), eq(EVENT_ID), eq(TASK_ID), eq(TaskAlertLevel.OverdueAlert12H),
                titleCaptor.capture(), anyString(), eq(REP_MEMBER_ID), eq(REP_ACCOUNT_ID));

        String title = titleCaptor.getValue();
        assertTrue(title.length() <= 200, "Title must fit the notification title budget: " + title.length());
        assertTrue(title.startsWith("Cảnh báo quá hạn 12h: "));
        assertTrue(title.endsWith("..."));
    }
}
