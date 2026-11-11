package com.familyfund.event.internal.repository;

import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.entity.EventTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for `EventTask` (table `event_tasks`).
 */
@Repository
public interface EventTaskRepository extends JpaRepository<EventTask, String> {

    Optional<EventTask> findByTaskId(String taskId);

    Optional<EventTask> findByTaskIdAndEvent_ClanId(String taskId, String clanId);

    List<EventTask> findAllByEventIdOrderByDeadlineAsc(String eventId);

    List<EventTask> findAllByEvent_EventIdAndEvent_ClanIdOrderByDeadlineAsc(String eventId, String clanId);

    List<EventTask> findAllByEvent_EventIdAndEvent_ClanIdAndStatusOrderByDeadlineAsc(
            String eventId, String clanId, TaskStatus status);

    List<EventTask> findAllByEvent_EventIdAndEvent_ClanIdAndAssignedMemberIdOrderByDeadlineAsc(
            String eventId, String clanId, String assignedMemberId);

    List<EventTask> findAllByEvent_EventIdAndEvent_ClanIdAndAssignedBranchIdOrderByDeadlineAsc(
            String eventId, String clanId, String assignedBranchId);

    boolean existsByTaskId(String taskId);

    long countByEvent_EventIdAndEvent_ClanId(String eventId, String clanId);

    /**
     * Finds every unfinished task whose deadline falls inside the window {@code (now, threshold]}.
     *
     * <p>US-08 background worker query: it runs on the scheduler thread, i.e. <b>outside</b> any HTTP
     * request, so there is no {@code TenantContext} and no {@code clan_id} filter on purpose — the
     * scan must cover every clan of the multi-tenant schema. Callers are responsible for resolving
     * the owning clan from the loaded event before notifying.</p>
     *
     * <p>Tasks completed ({@code completedStatus}) are excluded, as are tasks without an assigned
     * member (nobody to notify).</p>
     *
     * @param now             reference instant; only strictly future deadlines are considered
     * @param threshold       upper bound of the warning window (typically {@code now + 24h})
     * @param completedStatus terminal status excluded from the scan
     * @return unfinished assigned tasks ordered by the nearest deadline first
     */
    @Query("""
            SELECT t FROM EventTask t
            WHERE t.status <> :completedStatus
              AND t.assignedMemberId IS NOT NULL
              AND t.deadline > :now
              AND t.deadline <= :threshold
            ORDER BY t.deadline ASC
            """)
    List<EventTask> findUnfinishedTasksDueWithin(
            @Param("now") Instant now,
            @Param("threshold") Instant threshold,
            @Param("completedStatus") TaskStatus completedStatus);
}

