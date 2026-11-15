package com.familyfund.finance.internal.scheduler;

import com.familyfund.finance.api.AdvanceVoucherOverdueService;
import com.familyfund.finance.api.dto.OverdueScanSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Background scheduled job for US-48: scans advance vouchers past their settlement deadline
 * that remain in ChuaTatToan status, transitions them to QuaHan, and dispatches warnings.
 *
 * <p>Switched off with {@code app.finance.advance-overdue.enabled=false} (e.g. in test suites
 * so background passes do not unexpectedly mutate test data).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.finance.advance-overdue.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AdvanceVoucherOverdueScheduler {

    private final AdvanceVoucherOverdueService advanceVoucherOverdueService;

    @Scheduled(
            fixedDelayString = "${app.finance.advance-overdue.interval-ms:900000}",
            initialDelayString = "${app.finance.advance-overdue.initial-delay-ms:60000}"
    )
    public void scanAndProcessOverdueAdvanceVouchers() {
        try {
            OverdueScanSummary summary = advanceVoucherOverdueService.scanAndProcessOverdueVouchers(Instant.now());
            if (summary.overdueTransitionedCount() > 0) {
                log.info("US-48 scheduled pass: {} voucher(s) transitioned to QuaHan, {} notification(s) sent",
                        summary.overdueTransitionedCount(), summary.notificationsSentCount());
            } else {
                log.debug("US-48 scheduled pass finished: no new overdue advance vouchers found");
            }
        } catch (RuntimeException ex) {
            log.error("US-48 scheduled overdue pass failed; next scheduled pass will retry", ex);
        }
    }
}
