package com.familyfund.finance.internal.service;

import com.familyfund.core.tenant.TenantContext;
import com.familyfund.finance.api.AdvanceVoucherOverdueService;
import com.familyfund.finance.api.dto.OverdueScanSummary;
import com.familyfund.finance.internal.dto.OverdueProcessingResult;
import com.familyfund.finance.internal.entity.AdvanceVoucher;
import com.familyfund.finance.internal.repository.AdvanceVoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service implementation for scanning and processing overdue advance vouchers (US-48).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvanceVoucherOverdueServiceImpl implements AdvanceVoucherOverdueService {

    private final AdvanceVoucherRepository advanceVoucherRepository;
    private final AdvanceVoucherOverdueProcessor overdueProcessor;

    @Override
    public OverdueScanSummary scanAndProcessOverdueVouchers(Instant referenceTime) {
        Instant effectiveRefTime = referenceTime != null ? referenceTime : Instant.now();
        log.info("Starting cross-clan advance voucher overdue scan at {}", effectiveRefTime);

        // Clear tenant context because background scans evaluate all clans
        TenantContext.clear();

        List<AdvanceVoucher> overdueVouchers = advanceVoucherRepository.findOverdueVouchers(effectiveRefTime);
        int scannedCount = overdueVouchers.size();
        int transitionedCount = 0;
        int notificationsSentCount = 0;

        for (AdvanceVoucher voucher : overdueVouchers) {
            try {
                OverdueProcessingResult result =
                        overdueProcessor.processOverdueVoucher(voucher.getAdvanceVoucherId(), effectiveRefTime);
                if (result.transitioned()) {
                    transitionedCount++;
                }
                notificationsSentCount += result.notificationsSent();
            } catch (Exception ex) {
                log.error("Failed to process overdue advance voucher {}: {}",
                        voucher.getAdvanceVoucherId(), ex.getMessage(), ex);
            }
        }

        log.info("Advance voucher overdue scan completed: {} scanned, {} transitioned to QuaHan, {} notifications sent",
                scannedCount, transitionedCount, notificationsSentCount);

        return new OverdueScanSummary(scannedCount, transitionedCount, notificationsSentCount, effectiveRefTime);
    }

    @Override
    public OverdueScanSummary scanAndProcessOverdueVouchersForClan(String clanId, Instant referenceTime) {
        if (clanId == null || clanId.isBlank()) {
            return scanAndProcessOverdueVouchers(referenceTime);
        }

        Instant effectiveRefTime = referenceTime != null ? referenceTime : Instant.now();
        log.info("Starting clan-scoped advance voucher overdue scan for clan {} at {}", clanId, effectiveRefTime);

        TenantContext.setClanId(clanId);
        List<AdvanceVoucher> overdueVouchers =
                advanceVoucherRepository.findOverdueVouchersByClanId(clanId.trim(), effectiveRefTime);

        int scannedCount = overdueVouchers.size();
        int transitionedCount = 0;
        int notificationsSentCount = 0;

        for (AdvanceVoucher voucher : overdueVouchers) {
            try {
                OverdueProcessingResult result =
                        overdueProcessor.processOverdueVoucher(voucher.getAdvanceVoucherId(), effectiveRefTime);
                if (result.transitioned()) {
                    transitionedCount++;
                }
                notificationsSentCount += result.notificationsSent();
            } catch (Exception ex) {
                log.error("Failed to process clan-scoped overdue advance voucher {}: {}",
                        voucher.getAdvanceVoucherId(), ex.getMessage(), ex);
            }
        }

        return new OverdueScanSummary(scannedCount, transitionedCount, notificationsSentCount, effectiveRefTime);
    }
}
