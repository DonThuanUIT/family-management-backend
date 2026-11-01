package com.familyfund.event.internal.service;

import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.dto.request.AssignTaskRequest;
import com.familyfund.event.internal.dto.request.CreateTaskRequest;
import com.familyfund.event.internal.dto.response.TaskResponse;

import java.util.List;

/**
 * Service contract for managing event tasks and assignments (US-07).
 */
public interface TaskService {

    /**
     * Creates a new task for an event, optionally with an initial assignee.
     * Transitions status to InProgress if assigned, otherwise Unassigned.
     */
    TaskResponse createTask(String eventId, CreateTaskRequest request);

    /**
     * Assigns or reassigns an existing task to an individual member and/or family branch.
     * Transitions status from Unassigned to InProgress.
     */
    TaskResponse assignTask(String eventId, String taskId, AssignTaskRequest request);

    /**
     * Retrieves all tasks of an event with optional filters.
     */
    List<TaskResponse> getTasksByEvent(String eventId, TaskStatus status, String assignedMemberId, String assignedBranchId);

    /**
     * Retrieves details of a specific task.
     */
    TaskResponse getTaskDetail(String eventId, String taskId);
}

