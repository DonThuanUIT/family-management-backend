package com.familyfund.notification.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.core.exception.BadRequestException;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationService;
import com.familyfund.notification.api.dto.DeviceTokenResponse;
import com.familyfund.notification.api.dto.NotificationResponse;
import com.familyfund.notification.api.dto.RegisterDeviceTokenRequest;
import com.familyfund.notification.api.dto.UnreadCountResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Personal notification inbox of the authenticated caller (US-39).
 *
 * <p>Every endpoint is self-scoped: the caller's {@code account_id} comes from the JWT, never from the request, so
 * no endpoint accepts an account parameter and one account cannot reach another account's inbox.</p>
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    /**
     * Lists the caller's notifications, newest first.
     *
     * @param category optional {@code Event} | {@code Task} | {@code Finance} (unknown values answer 400)
     * @param isRead   optional read-state filter ({@code false} = unread only)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> list(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "isRead", required = false) Boolean isRead,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Page<NotificationResponse> result = notificationService.findMyNotifications(
                parseCategory(category), isRead,
                PageRequest.of(normalizePage(page), normalizeSize(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(result, "Lấy danh sách thông báo thành công"));
    }

    /** Unread badge of the caller's inbox: total plus one counter per category. */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success(notificationService.unreadSummary(),
                "Lấy số thông báo chưa đọc thành công"));
    }

    /** Marks one of the caller's notifications as read ({@code is_read = true}). */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable String notificationId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markAsRead(notificationId),
                "Đã đánh dấu thông báo là đã đọc"));
    }

    /** Marks every unread notification of the caller as read, optionally limited to one category. */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(
            @RequestParam(name = "category", required = false) String category) {
        int updated = notificationService.markAllAsRead(parseCategory(category));
        return ResponseEntity.ok(ApiResponse.success(Map.of("markedCount", updated),
                "Đã đánh dấu tất cả thông báo là đã đọc"));
    }

    /** Registers the FCM registration token of the caller's device so push notifications can reach it. */
    @PostMapping("/device-tokens")
    public ResponseEntity<ApiResponse<DeviceTokenResponse>> registerDeviceToken(
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        String tokenId = notificationService.registerDeviceToken(request.fcmToken(), request.platform());
        String platform = (request.platform() == null || request.platform().isBlank())
                ? "Android" : request.platform().trim();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new DeviceTokenResponse(tokenId, platform),
                        "Đăng ký thiết bị nhận thông báo thành công"));
    }

    /** Deactivates the caller's device token (called on logout). */
    @DeleteMapping("/device-tokens")
    public ResponseEntity<ApiResponse<Void>> unregisterDeviceToken(@RequestParam(name = "fcmToken") String fcmToken) {
        notificationService.unregisterDeviceToken(fcmToken);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã huỷ đăng ký thiết bị nhận thông báo"));
    }

    private static int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Parses the optional {@code category} query parameter.
     *
     * <p>Done by hand instead of binding the enum directly so an unknown value answers 400 with the standard error
     * envelope ({@code BadRequestException}) instead of Spring's generic type-mismatch 500.</p>
     */
    private static NotificationCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String trimmed = category.trim();
        for (NotificationCategory candidate : NotificationCategory.values()) {
            if (candidate.name().equalsIgnoreCase(trimmed)) {
                return candidate;
            }
        }
        throw new BadRequestException("Nhóm thông báo không hợp lệ: '" + trimmed
                + "'. Chỉ chấp nhận: Event, Task, Finance");
    }
}
