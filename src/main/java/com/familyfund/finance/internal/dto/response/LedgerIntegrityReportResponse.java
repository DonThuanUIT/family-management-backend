package com.familyfund.finance.internal.dto.response;

import com.familyfund.finance.internal.util.ImmutableLedgerHashCalculator;

import java.time.Instant;
import java.util.List;

/**
 * Audit report produced by verifying the SHA-256 blockchain-style chain of a clan's
 * {@code immutable_ledgers} (NFR-01 / US-61).
 */
public record LedgerIntegrityReportResponse(
        String clanId,
        boolean valid,
        int totalEntries,
        int verifiedEntries,
        String genesisAnchor,
        String latestTransactionId,
        String latestRecordHash,
        List<BrokenLinkResponse> brokenLinks,
        Instant verifiedAt
) {

    public record BrokenLinkResponse(
            String transactionId,
            int chainIndex,
            String reason,
            String expectedPreviousHash,
            String actualPreviousHash,
            String expectedRecordHash,
            String actualRecordHash
    ) {

        public static BrokenLinkResponse from(ImmutableLedgerHashCalculator.BrokenLinkDetail detail) {
            return new BrokenLinkResponse(
                    detail.transactionId(),
                    detail.chainIndex(),
                    detail.reason(),
                    detail.expectedPreviousHash(),
                    detail.actualPreviousHash(),
                    detail.expectedRecordHash(),
                    detail.actualRecordHash()
            );
        }
    }
}
