package com.familyfund.notification.api;

/**
 * The three notification groups of the personal inbox (US-39).
 *
 * <p>Stored as {@code notifications.category VARCHAR(20)} with the matching
 * {@code CHECK (category IN ('Event', 'Task', 'Finance'))} constraint, so the enum names are part of the
 * database contract and must not be renamed without a migration.</p>
 */
public enum NotificationCategory {

    /** Thông báo sự kiện: mở đăng ký, thay đổi lịch trình, phân quyền trong sự kiện. */
    Event,

    /** Thông báo công việc: phân công, nhắc hạn 24h, leo thang quá hạn 12h. */
    Task,

    /** Thông báo tài chính: ghi nhận đóng góp, phiếu thu, tạm ứng/quyết toán. */
    Finance
}
