package com.familyfund.event.internal.repository;

import com.familyfund.event.api.dto.TaskAlertLevel;
import com.familyfund.event.internal.entity.TaskProgressAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA Repository for `TaskProgressAlert` (table `task_progress_alerts`, US-08).
 */
@Repository
public interface TaskProgressAlertRepository extends JpaRepository<TaskProgressAlert, String> {

    boolean existsByTaskIdAndAlertLevel(String taskId, TaskAlertLevel alertLevel);

    List<TaskProgressAlert> findAllByTaskIdOrderBySentTimeDesc(String taskId);
}