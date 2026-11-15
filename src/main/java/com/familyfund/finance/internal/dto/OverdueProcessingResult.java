package com.familyfund.finance.internal.dto;

/**
 * Result of evaluating and processing a single overdue advance voucher (US-48).
 *
 * @param advanceVoucherId  identifier of the voucher
 * @param transitioned      true if voucher status transitioned from ChuaTatToan to QuaHan
 * @param notificationsSent count of warning notifications dispatched for this voucher
 */
public record OverdueProcessingResult(
        String advanceVoucherId,
        boolean transitioned,
        int notificationsSent
) {}
