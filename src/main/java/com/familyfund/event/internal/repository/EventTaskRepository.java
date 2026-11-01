package com.familyfund.event.internal.repository;

import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.entity.EventTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}

