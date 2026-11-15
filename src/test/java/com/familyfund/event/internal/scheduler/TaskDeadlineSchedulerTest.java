package com.familyfund.event.internal.scheduler;

import com.familyfund.event.internal.service.TaskProgressAlertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the US-08 scheduled entry point.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskDeadlineScheduler Unit Tests")
class TaskDeadlineSchedulerTest {

    @Mock
    private TaskProgressAlertService taskProgressAlertService;

    @InjectMocks
    private TaskDeadlineScheduler scheduler;

    @Test
    @DisplayName("Delegates each scheduled pass to the progress alert service")
    void delegatesToService() {
        when(taskProgressAlertService.scanAndWarnUpcomingDeadlines()).thenReturn(3);

        scheduler.scanUpcomingTaskDeadlines();

        verify(taskProgressAlertService).scanAndWarnUpcomingDeadlines();
    }

    @Test
    @DisplayName("Swallows unexpected failures so the scheduled task keeps running on later passes")
    void swallowsFailures() {
        when(taskProgressAlertService.scanAndWarnUpcomingDeadlines())
                .thenThrow(new IllegalStateException("database unreachable"));

        assertDoesNotThrow(() -> scheduler.scanUpcomingTaskDeadlines());

        verify(taskProgressAlertService).scanAndWarnUpcomingDeadlines();
    }

    @Test
    @DisplayName("Delegates each escalation pass to the overdue scan")
    void delegatesEscalationPass() {
        when(taskProgressAlertService.scanAndEscalateOverdueTasks()).thenReturn(2);

        scheduler.scanOverdueTaskDeadlines();

        verify(taskProgressAlertService).scanAndEscalateOverdueTasks();
    }

    @Test
    @DisplayName("Swallows escalation failures so the scheduled task keeps running on later passes")
    void swallowsEscalationFailures() {
        when(taskProgressAlertService.scanAndEscalateOverdueTasks())
                .thenThrow(new IllegalStateException("database unreachable"));

        assertDoesNotThrow(() -> scheduler.scanOverdueTaskDeadlines());

        verify(taskProgressAlertService).scanAndEscalateOverdueTasks();
    }
}