package com.familyfund.event.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.dto.request.AssignTaskRequest;
import com.familyfund.event.internal.dto.request.CreateTaskRequest;
import com.familyfund.event.internal.dto.response.TaskResponse;
import com.familyfund.event.internal.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for managing event preparation tasks and assignments (US-07).
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /**
     * Tạo hạng mục công việc mới trong sự kiện (Ban tổ chức sự kiện, Trưởng tộc, hoặc Admin).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer', 'OrganizerMember')")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @PathVariable String eventId,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        TaskResponse response = taskService.createTask(eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Khởi tạo công việc thành công"));
    }

    /**
     * Phân công công việc cho cá nhân và/hoặc chi nhánh gia phả (Ban tổ chức, Trưởng tộc, hoặc Admin).
     * Tự động chuyển trạng thái công việc sang 'InProgress' và gửi thông báo tới người nhận.
     */
    @PostMapping("/{taskId}/assign")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer', 'OrganizerMember')")
    public ResponseEntity<ApiResponse<TaskResponse>> assignTask(
            @PathVariable String eventId,
            @PathVariable String taskId,
            @Valid @RequestBody AssignTaskRequest request
    ) {
        TaskResponse response = taskService.assignTask(eventId, taskId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Phân công công việc thành công"));
    }

    /**
     * Lấy danh sách công việc của sự kiện kèm bộ lọc tuỳ chọn (trạng thái, người nhận, nhánh phụ trách).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(
            @PathVariable String eventId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String assignedMemberId,
            @RequestParam(required = false) String assignedBranchId
    ) {
        List<TaskResponse> response = taskService.getTasksByEvent(eventId, status, assignedMemberId, assignedBranchId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Xem thông tin chi tiết một hạng mục công việc trong sự kiện.
     */
    @GetMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TaskResponse>> getTaskDetail(
            @PathVariable String eventId,
            @PathVariable String taskId
    ) {
        TaskResponse response = taskService.getTaskDetail(eventId, taskId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
