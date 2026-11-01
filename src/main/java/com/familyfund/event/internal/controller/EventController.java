package com.familyfund.event.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.event.internal.dto.request.AssignPersonnelRequest;
import com.familyfund.event.internal.dto.request.CreateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventStatusRequest;
import com.familyfund.event.internal.dto.response.EventDetailResponse;
import com.familyfund.event.internal.dto.response.EventNotificationResponse;
import com.familyfund.event.internal.dto.response.EventPersonnelResponse;
import com.familyfund.event.internal.dto.response.EventResponse;
import com.familyfund.event.internal.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing family events, status transitions, personnel assignments,
 * and event notifications / schedule updates.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * Khởi tạo sự kiện mới (chỉ Trưởng tộc và Admin).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Khởi tạo sự kiện thành công"));
    }

    /**
     * Cập nhật thông tin sự kiện và phát thông báo nếu có thay đổi thời gian hoặc địa điểm.
     */
    @PutMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer')")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable String eventId,
            @Valid @RequestBody UpdateEventRequest request) {
        EventResponse response = eventService.updateEvent(eventId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật thông tin sự kiện thành công"));
    }

    /**
     * Lấy danh sách sự kiện của dòng họ hiện tại.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<EventResponse>>> listEvents() {
        List<EventResponse> response = eventService.listEvents();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Xem thông tin chi tiết sự kiện kèm danh sách ban tổ chức và thủ quỹ.
     */
    @GetMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<EventDetailResponse>> getEventDetail(@PathVariable String eventId) {
        EventDetailResponse response = eventService.getEventDetail(eventId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Chuyển đổi trạng thái vòng đời sự kiện (Trưởng tộc, Admin, hoặc Trưởng ban tổ chức sự kiện).
     */
    @PatchMapping("/{eventId}/status")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer')")
    public ResponseEntity<ApiResponse<EventResponse>> updateEventStatus(
            @PathVariable String eventId,
            @Valid @RequestBody UpdateEventStatusRequest request) {
        EventResponse response = eventService.updateEventStatus(eventId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật trạng thái sự kiện thành công"));
    }

    /**
     * Chỉ định Ban tổ chức và Thủ quỹ gắn riêng với sự kiện (chỉ Trưởng tộc và Admin).
     */
    @PostMapping("/{eventId}/personnel")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<EventPersonnelResponse>>> assignPersonnel(
            @PathVariable String eventId,
            @Valid @RequestBody AssignPersonnelRequest request) {
        List<EventPersonnelResponse> response = eventService.assignPersonnel(eventId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Chỉ định ban tổ chức và thủ quỹ thành công"));
    }

    /**
     * Xem lịch sử thông báo đã phát đi của sự kiện.
     */
    @GetMapping("/{eventId}/notifications")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<EventNotificationResponse>>> getEventNotifications(
            @PathVariable String eventId) {
        List<EventNotificationResponse> response = eventService.getEventNotifications(eventId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
