package com.familyfund.event.internal.service;

import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.entity.EventTask;
import com.familyfund.event.internal.repository.EventTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the US-08 deadline scan pass.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskProgressAlertServiceImpl Unit Tests")
class TaskProgressAlertServiceImplTest {

    @Mock
    private EventTaskRepository eventTaskRepository;

    @Mock
    private TaskProgressWarningIssuer warningIssuer;

    @Mock
    private TaskOverdueEscalationIssuer overdueEscalationIssuer;

    @InjectMocks
    private TaskProgressAlertServiceImpl service;

    private EventTask task(String taskId) {
        return EventTask.builder()
                .taskId(taskId)
                .eventId("EVT_TEST_001")
                .taskName("Chuẩn bị lễ vật")
                .deadline(Instant.now().plus(Duration.ofHours(5)))
                .status(TaskStatus.InProgress)
                .assignedMemberId("MEM_TEST_001")
                .createdBy("ACC_CREATOR")
                .build();
    }

    @Test
    @DisplayName("Scans the 24h window and counts only the warnings actually issued")
    void countsIssuedWarnings() {
        EventTask dueSoon = task("TSK_1");
        EventTask alreadyWarned = task("TSK_2");
        when(eventTaskRepository.findUnfinishedTasksDueWithin(any(), any(), eq(TaskStatus.Completed)))
                .thenReturn(List.of(dueSoon, alreadyWarned));
        when(warningIssuer.issueProgressWarning(eq("TSK_1"), any())).thenReturn(true);
        when(warningIssuer.issueProgressWarning(eq("TSK_2"), any())).thenReturn(false);

        int issued = service.scanAndWarnUpcomingDeadlines();

        assertEquals(1, issued);
        verify(warningIssuer, times(2)).issueProgressWarning(any(String.class), any(Instant.class));
    }

    @Test
    @DisplayName("Uses a 24 hour window starting from the scan instant")
    void usesTwentyFourHourWindow() {
        when(eventTaskRepository.findUnfinishedTasksDueWithin(any(), any(), eq(TaskStatus.Completed)))
                .thenReturn(List.of());

        service.scanAndWarnUpcomingDeadlines();

        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(eventTaskRepository).findUnfinishedTasksDueWithin(
                nowCaptor.capture(), thresholdCaptor.capture(), eq(TaskStatus.Completed));

        Duration window = Duration.between(nowCaptor.getValue(), thresholdCaptor.getValue());
        assertEquals(Duration.ofHours(24), window);
    }

    @Test
    @DisplayName("A failing task does not abort the pass: remaining tasks are still processed")
    void isolatesFailuresPerTask() {
        EventTask failing = task("TSK_BOOM");
        EventTask healthy = task("TSK_OK");
        when(eventTaskRepository.findUnfinishedTasksDueWithin(any(), any(), eq(TaskStatus.Completed)))
                .thenReturn(List.of(failing, healthy));
        when(warningIssuer.issueProgressWarning(eq("TSK_BOOM"), any()))
                .thenThrow(new IllegalStateException("notification provider down"));
        when(warningIssuer.issueProgressWarning(eq("TSK_OK"), any())).thenReturn(true);

        int issued = service.scanAndWarnUpcomingDeadlines();

        assertEquals(1, issued);
        verify(warningIssuer).issueProgressWarning(eq("TSK_OK"), any());
    }

    @Test
    @DisplayName("Returns zero and skips processing when no task is due within the window")
    void returnsZeroWhenNoCandidate() {
        when(eventTaskRepository.findUnfinishedTasksDueWithin(any(), any(), eq(TaskStatus.Completed)))
                .thenReturn(List.of());

        int issued = service.scanAndWarnUpcomingDeadlines();

        assertEquals(0, issued);
        verify(warningIssuer, times(0)).issueProgressWarning(any(), any());
    }

    // ------------------------------------------------ US-47 escalation pass (12h overdue)

    @Test
    @DisplayName("Escalation pass counts only the escalations actually issued")
    void countsIssuedEscalations() {
        when(eventTaskRepository.findUnfinishedTasksOverdueBefore(any(), eq(TaskStatus.Completed)))
                .thenReturn(List.of(task("TSK_LATE_1"), task("TSK_LATE_2")));
        when(overdueEscalationIssuer.issueOverdueEscalation(eq("TSK_LATE_1"), any())).thenReturn(true);
        when(overdueEscalationIssuer.issueOverdueEscalation(eq("TSK_LATE_2"), any())).thenReturn(false);

        int issued = service.scanAndEscalateOverdueTasks();

        assertEquals(1, issued);
        verify(overdueEscalationIssuer, times(2)).issueOverdueEscalation(any(String.class), any(Instant.class));
    }

    @Test
    @DisplayName("Escalation pass looks for deadlines at least 12 hours in the past")
    void usesTwelveHourOverdueThreshold() {
        when(eventTaskRepository.findUnfinishedTasksOverdueBefore(any(), eq(TaskStatus.Completed)))
                .thenReturn(List.of());

        service.scanAndEscalateOverdueTasks();

        ArgumentCaptor<Instant> overdueBeforeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(eventTaskRepository).findUnfinishedTasksOverdueBefore(
                overdueBeforeCaptor.capture(), eq(TaskStatus.Completed));

        Duration lateness = Duration.between(overdueBeforeCaptor.getValue(), Instant.now());
        assertTrue(lateness.compareTo(Duration.ofHours(12)) >= 0,
                "Threshold must be at least 12h before the scan instant, was " + lateness);
        assertTrue(lateness.compareTo(Duration.ofHours(12).plusMinutes(1)) < 0,
                "Threshold must be computed from the scan instant, was " + lateness);
    }

    @Test
    @DisplayName("A failing escalation does not abort the pass: remaining overdue tasks are still escalated")
    void isolatesEscalationFailuresPerTask() {
        when(eventTaskRepository.findUnfinishedTasksOverdueBefore(any(), eq(TaskStatus.Completed)))
                .thenReturn(List.of(task("TSK_LATE_BOOM"), task("TSK_LATE_OK")));
        when(overdueEscalationIssuer.issueOverdueEscalation(eq("TSK_LATE_BOOM"), any()))
                .thenThrow(new IllegalStateException("notification provider down"));
        when(overdueEscalationIssuer.issueOverdueEscalation(eq("TSK_LATE_OK"), any())).thenReturn(true);

        int issued = service.scanAndEscalateOverdueTasks();

        assertEquals(1, issued);
        verify(overdueEscalationIssuer).issueOverdueEscalation(eq("TSK_LATE_OK"), any());
    }
}