package com.familyfund.finance.api;

import com.familyfund.finance.api.dto.OverdueScanSummary;

import java.time.Instant;

/**
 * Public service interface for scanning and processing overdue advance vouchers (US-48).
 */
public interface AdvanceVoucherOverdueService {

    /**
     * Scans cross-clan for all advance vouchers in ChuaTatToan status whose settlement deadline
     * has elapsed relative to {@code referenceTime}, transitions their status to QuaHan,
     * and sends simultaneous warning alerts to recipients and organizers.
     *
     * @param referenceTime timestamp representing 'now' (null defaults to {@link Instant#now()})
     * @return summary metrics of the scan pass
     */
    OverdueScanSummary scanAndProcessOverdueVouchers(Instant referenceTime);

    /**
     * Scans overdue advance vouchers scoped to a specific clan.
     *
     * @param clanId        clan identifier
     * @param referenceTime timestamp representing 'now' (null defaults to {@link Instant#now()})
     * @return summary metrics of the scan pass
     */
    OverdueScanSummary scanAndProcessOverdueVouchersForClan(String clanId, Instant referenceTime);
}
