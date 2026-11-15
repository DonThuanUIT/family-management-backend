package com.familyfund.finance.api.dto;

import java.time.Instant;

/**
 * Summary metrics of an advance voucher overdue scanning pass (US-48).
 *
 * @param scannedCount             total number of vouchers identified with past deadline in un-settled status
 * @param overdueTransitionedCount number of vouchers transitioned from ChuaTatToan to QuaHan
 * @param notificationsSentCount   total number of warning notifications dispatched (recipient + event organizers)
 * @param referenceTime            the timestamp used as 'now' to determine overdue deadlines
 */
public record OverdueScanSummary(
        int scannedCount,
        int overdueTransitionedCount,
        int notificationsSentCount,
        Instant referenceTime
) {}
