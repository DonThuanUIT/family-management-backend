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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the US-08 single-task progress warning issuer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskProgressWarningIssuer Unit Tests")
class TaskProgressWarningIssuerTest {

    private static final String CLAN_ID = "CLAN_TEST";
    private static final String EVENT_ID = "EVT_TEST_001";
    private static final String TASK_ID = "TSK_TEST_001";
    private static final String MEMBER_ID = "MEM_TEST_001";
    private static final String BRANCH_ID = "BR_TEST_001";
    private static final String ACCOUNT_ID = "ACC_TEST_001";
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
    private IdentityRoleCommandService identityRoleCommandService;

    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private TaskProgressWarningIssuer issuer;

    private EventTask task(String taskName, Instant deadline, TaskStatus status, String assignedMemberId) {
        return EventTask.builder()
                .taskId(TASK_ID)
                .eventId(EVENT_ID)
                .taskName(taskName)
                .advanceBudget(new BigDecimal("3500000.00"))
                .deadline(deadline)
                .status(status)
                .assignedBranchId(BRANCH_ID)
                .assignedMemberId(assignedMemberId)
                .createdBy("ACC_CREATOR")
                .build();
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

    private void stubHappyPath(EventTask task, EventStatus eventStatus) {
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event(eventStatus)));
        when(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_ID, TaskAlertLevel.Reminder24H))
                .thenReturn(false);
        when(alertIdGenerator.generate()).thenReturn(ALERT_ID);
        when(identityRoleCommandService.findAccountByMemberId(MEMBER_ID)).thenReturn(Optional.of(
                new AccountSummaryDto(ACCOUNT_ID, MEMBER_ID, "0900000001", null, "Active", Set.of("ROLE_MEMBER"))));
    }

    @Test
    @DisplayName("Issues a Reminder24H ProgressWarning log and notifies the assigned member when deadline is within 24h")
    void issuesWarningWhenDeadlineWithinWindow() {
        EventTask task = task("Mua sắm hoa quả, trầu cau", NOW.plus(Duration.ofHours(5)), TaskStatus.InProgress, MEMBER_ID);
        stubHappyPath(task, EventStatus.Ongoing);

        boolean warned = issuer.issueProgressWarning(TASK_ID, NOW);

        assertTrue(warned);

        ArgumentCaptor<TaskProgressAlert> captor = ArgumentCaptor.forClass(TaskProgressAlert.class);
        verify(taskProgressAlertRepository).save(captor.capture());
        TaskProgressAlert saved = captor.getValue();

        assertEquals(ALERT_ID, saved.getAlertId());
        assertEquals(TASK_ID, saved.getTaskId());
        assertEquals(TaskAlertLevel.Reminder24H, saved.getAlertLevel());
        assertEquals(ACCOUNT_ID, saved.getRecipientAccountId());
        assertEquals(NOW, saved.getSentTime());
        assertFalse(saved.getIsRead());
        assertTrue(saved.getAlertContent().contains("Mua sắm hoa quả, trầu cau"));
        assertTrue(saved.getAlertContent().contains("Giỗ Tổ 2026"));
        assertTrue(saved.getAlertContent().contains("5 giờ 0 phút"));
        assertTrue(saved.getAlertContent().contains("3500000.00 VNĐ"));
        assertTrue(saved.getAlertContent().contains(MEMBER_ID));

        verify(notificationSender).sendTaskProgressWarning(
                eq(CLAN_ID),
                eq(EVENT_ID),
                eq(TASK_ID),
                eq(TaskAlertLevel.Reminder24H),
                any(String.class),
                eq(saved.getAlertContent()),
                eq(MEMBER_ID),
                eq(ACCOUNT_ID)
        );
    }

    @Test
    @DisplayName("Treats a deadline of exactly 24 hours as inside the warning window")
    void warnsAtExactBoundary() {
        EventTask task = task("Thuê rạp và bàn ghế", NOW.plus(TaskProgressWarningIssuer.WARNING_WINDOW),
                TaskStatus.InProgress, MEMBER_ID);
        stubHappyPath(task, EventStatus.Ongoing);

        assertTrue(issuer.issueProgressWarning(TASK_ID, NOW));
        verify(taskProgressAlertRepository).save(any(TaskProgressAlert.class));
    }

    @Test
    @DisplayName("Skips tasks that are already completed")
    void skipsCompletedTask() {
        when(eventTaskRepository.findById(TASK_ID))
                .thenReturn(Optional.of(task("Đã xong", NOW.plus(Duration.ofHours(2)), TaskStatus.Completed, MEMBER_ID)));

        assertFalse(issuer.issueProgressWarning(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips tasks whose deadline is more than 24 hours away")
    void skipsTaskOutsideWindow() {
        when(eventTaskRepository.findById(TASK_ID))
                .thenReturn(Optional.of(task("Còn xa", NOW.plus(Duration.ofHours(25)), TaskStatus.Unassigned, MEMBER_ID)));

        assertFalse(issuer.issueProgressWarning(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips tasks without an assigned member")
    void skipsTaskWithoutAssignee() {
        when(eventTaskRepository.findById(TASK_ID))
                .thenReturn(Optional.of(task("Chưa phân công", NOW.plus(Duration.ofHours(3)), TaskStatus.Unassigned, null)));

        assertFalse(issuer.issueProgressWarning(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Is idempotent: a task that already received a Reminder24H warning is skipped")
    void skipsAlreadyWarnedTask() {
        EventTask task = task("Đã cảnh báo", NOW.plus(Duration.ofHours(4)), TaskStatus.InProgress, MEMBER_ID);
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_ID, TaskAlertLevel.Reminder24H))
                .thenReturn(true);

        assertFalse(issuer.issueProgressWarning(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips when the event is closed (read-only bookkeeping)")
    void skipsClosedEvent() {
        EventTask task = task("Sự kiện đã khóa sổ", NOW.plus(Duration.ofHours(4)), TaskStatus.InProgress, MEMBER_ID);
        stubHappyPath(task, EventStatus.Closed);

        assertFalse(issuer.issueProgressWarning(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips when the assigned member has no linked user account (no delivery channel)")
    void skipsWhenMemberHasNoAccount() {
        EventTask task = task("Hồ sơ ảo", NOW.plus(Duration.ofHours(4)), TaskStatus.InProgress, MEMBER_ID);
        when(eventTaskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event(EventStatus.Ongoing)));
        when(taskProgressAlertRepository.existsByTaskIdAndAlertLevel(TASK_ID, TaskAlertLevel.Reminder24H))
                .thenReturn(false);
        when(identityRoleCommandService.findAccountByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        assertFalse(issuer.issueProgressWarning(TASK_ID, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Skips unknown task IDs and blank input safely")
    void skipsUnknownOrBlankTaskId() {
        when(eventTaskRepository.findById(any(String.class))).thenReturn(Optional.empty());

        assertFalse(issuer.issueProgressWarning("TSK_UNKNOWN", NOW));
        assertFalse(issuer.issueProgressWarning("  ", NOW));
        assertFalse(issuer.issueProgressWarning(null, NOW));

        verify(taskProgressAlertRepository, never()).save(any(TaskProgressAlert.class));
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("Truncates long task names so the notification title never exceeds 200 characters")
    void truncatesLongTitle() {
        EventTask task = task("A".repeat(250), NOW.plus(Duration.ofHours(2)), TaskStatus.InProgress, MEMBER_ID);
        stubHappyPath(task, EventStatus.Ongoing);

        assertTrue(issuer.issueProgressWarning(TASK_ID, NOW));

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).sendTaskProgressWarning(
                eq(CLAN_ID), eq(EVENT_ID), eq(TASK_ID), eq(TaskAlertLevel.Reminder24H),
                titleCaptor.capture(), any(String.class), eq(MEMBER_ID), eq(ACCOUNT_ID));

        String title = titleCaptor.getValue();
        assertTrue(title.length() <= 200, "Title must fit the notification title budget: " + title.length());
        assertTrue(title.endsWith("..."));
    }
}