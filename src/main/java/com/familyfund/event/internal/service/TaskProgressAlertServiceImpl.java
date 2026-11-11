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
}