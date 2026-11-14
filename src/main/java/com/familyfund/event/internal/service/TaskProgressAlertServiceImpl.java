package com.familyfund.event.internal.service;

import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.entity.EventTask;
import com.familyfund.event.internal.repository.EventTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Implementation of the US-08 deadline scan.
 *
 * <p>Runs outside any tenant context (scheduler thread) and therefore queries tasks across all
 * clans. Each candidate is processed in its own transaction by
 * {@link TaskProgressWarningIssuer}, so one failing notification neither rolls back the whole
 * pass nor leaves a warning logged without being sent.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskProgressAlertServiceImpl implements TaskProgressAlertService {

    private final EventTaskRepository eventTaskRepository;
    private final TaskProgressWarningIssuer warningIssuer;
    private final TaskOverdueEscalationIssuer overdueEscalationIssuer;

    @Override
    public int scanAndWarnUpcomingDeadlines() {
        Instant now = Instant.now();
        Instant threshold = now.plus(TaskProgressWarningIssuer.WARNING_WINDOW);

        List<EventTask> candidates = eventTaskRepository.findUnfinishedTasksDueWithin(
                now, threshold, TaskStatus.Completed);

        if (candidates.isEmpty()) {
            log.debug("US-08 deadline scan: no unfinished task due within {}h", TaskProgressWarningIssuer.WARNING_WINDOW.toHours());
            return 0;
        }

        log.info("US-08 deadline scan: {} unfinished task(s) due within {}h",
                candidates.size(), TaskProgressWarningIssuer.WARNING_WINDOW.toHours());

        int issued = 0;
        for (EventTask task : candidates) {
            try {
                if (warningIssuer.issueProgressWarning(task.getTaskId(), now)) {
                    issued++;
                }
            } catch (RuntimeException ex) {
                // Isolate failures: the remaining tasks of this pass must still be processed.
                log.error("US-08 deadline scan: failed to issue progress warning for task [{}]", task.getTaskId(), ex);
            }
        }

        log.info("US-08 deadline scan: issued {} progress warning(s)", issued);
        return issued;
    }

    @Override
    public int scanAndEscalateOverdueTasks() {
        Instant now = Instant.now();
        Instant overdueBefore = now.minus(TaskOverdueEscalationIssuer.ESCALATION_DELAY);

        List<EventTask> candidates = eventTaskRepository.findUnfinishedTasksOverdueBefore(
                overdueBefore, TaskStatus.Completed);

        if (candidates.isEmpty()) {
            log.debug("US-47 escalation scan: no unfinished task overdue by more than {}h",
                    TaskOverdueEscalationIssuer.ESCALATION_DELAY.toHours());
            return 0;
        }

        log.info("US-47 escalation scan: {} unfinished task(s) overdue by more than {}h",
                candidates.size(), TaskOverdueEscalationIssuer.ESCALATION_DELAY.toHours());

        int issued = 0;
        for (EventTask task : candidates) {
            try {
                if (overdueEscalationIssuer.issueOverdueEscalation(task.getTaskId(), now)) {
                    issued++;
                }
            } catch (RuntimeException ex) {
                // Isolate failures: the remaining overdue tasks of this pass must still be escalated.
                log.error("US-47 escalation scan: failed to escalate overdue task [{}]", task.getTaskId(), ex);
            }
        }

        log.info("US-47 escalation scan: issued {} overdue escalation(s)", issued);
        return issued;
    }
}