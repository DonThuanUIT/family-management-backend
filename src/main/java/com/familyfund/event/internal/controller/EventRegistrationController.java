package com.familyfund.event.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.event.internal.dto.request.RegisterAttendeesRequest;
import com.familyfund.event.internal.dto.request.RequestCancelRegistrationRequest;
import com.familyfund.event.internal.dto.request.RequestModifyRegistrationRequest;
import com.familyfund.event.internal.dto.request.ReviewRegistrationRequest;
import com.familyfund.event.internal.dto.response.RegistrationResponse;
import com.familyfund.event.internal.dto.response.RegistrationSummaryResponse;
import com.familyfund.event.internal.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for family event attendance registrations,
 * real-time aggregations, and modification/cancellation reviews (US-06).
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/registrations")
@RequiredArgsConstructor
public class EventRegistrationController {

    private final RegistrationService registrationService;

    /**
     * Nộp biểu mẫu đăng ký tham gia sự kiện (Thành viên / Người đại diện hộ gia đình).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RegistrationResponse>> registerAttendees(
            @PathVariable String eventId,
            @Valid @RequestBody RegisterAttendeesRequest request
    ) {
        RegistrationResponse response = registrationService.registerAttendees(eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Đăng ký tham gia sự kiện thành công"));
    }

    /**
     * Lấy số liệu tổng hợp cộng dồn cỗ bàn và người tham dự cho Ban tổ chức.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer', 'OrganizerMember', 'EventTreasurer')")
    public ResponseEntity<ApiResponse<RegistrationSummaryResponse>> getRegistrationSummary(
            @PathVariable String eventId
    ) {
        RegistrationSummaryResponse response = registrationService.getRegistrationSummary(eventId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Lấy danh sách tất cả các hộ gia đình đã đăng ký tham gia sự kiện (phân trang).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer', 'OrganizerMember')")
    public ResponseEntity<ApiResponse<Page<RegistrationResponse>>> listRegistrations(
            @PathVariable String eventId,
            @PageableDefault(size = 20, sort = "registrationDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<RegistrationResponse> page = registrationService.listRegistrations(eventId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    /**
     * Xem thông tin đăng ký của chính hộ gia đình người dùng đang đăng nhập.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RegistrationResponse>> getMyRegistration(
            @PathVariable String eventId
    ) {
        RegistrationResponse response = registrationService.getMyRegistration(eventId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Xem thông tin chi tiết một lượt đăng ký cụ thể.
     */
    @GetMapping("/{registrationId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RegistrationResponse>> getRegistrationDetail(
            @PathVariable String eventId,
            @PathVariable String registrationId
    ) {
        RegistrationResponse response = registrationService.getRegistrationById(eventId, registrationId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gửi yêu cầu thay đổi số lượng người / khẩu phần / phương tiện tham gia.
     */
    @PostMapping("/{registrationId}/request-modification")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RegistrationResponse>> requestModification(
            @PathVariable String eventId,
            @PathVariable String registrationId,
            @Valid @RequestBody RequestModifyRegistrationRequest request
    ) {
        RegistrationResponse response = registrationService.requestModification(eventId, registrationId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Gửi yêu cầu thay đổi thông tin đăng ký thành công"));
    }

    /**
     * Gửi yêu cầu hủy đăng ký tham gia sự kiện.
     */
    @PostMapping("/{registrationId}/request-cancellation")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<RegistrationResponse>> requestCancellation(
            @PathVariable String eventId,
            @PathVariable String registrationId,
            @Valid @RequestBody RequestCancelRegistrationRequest request
    ) {
        RegistrationResponse response = registrationService.requestCancellation(eventId, registrationId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Gửi yêu cầu hủy đăng ký thành công"));
    }

    /**
     * Ban tổ chức xét duyệt (chấp thuận hoặc từ chối) yêu cầu sửa đổi hoặc hủy đăng ký.
     */
    @PostMapping("/{registrationId}/review")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @eventSecurity.hasContextualRole(#eventId, 'HeadOfOrganizer')")
    public ResponseEntity<ApiResponse<RegistrationResponse>> reviewRegistration(
            @PathVariable String eventId,
            @PathVariable String registrationId,
            @Valid @RequestBody ReviewRegistrationRequest request
    ) {
        RegistrationResponse response = registrationService.reviewRegistrationRequest(eventId, registrationId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Xét duyệt yêu cầu đăng ký thành công"));
    }
}

