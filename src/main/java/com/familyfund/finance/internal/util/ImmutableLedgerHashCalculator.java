package com.familyfund.finance.internal.util;

import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic SHA-256 hash chaining calculator and verifier for {@code immutable_ledgers} (US-59 / US-61).
 */
@Component
public class ImmutableLedgerHashCalculator {

    public static final String GENESIS_HASH = "GENESIS";

    /**
     * Computes a 64-character lowercase hexadecimal SHA-256 hash chained to {@code previousHash}
     * without a disbursement reference (backward-compatible 13-arg signature for US-59).
     */
    public String computeRecordHash(
            String previousHash,
            String transactionId,
            String clanId,
            String eventId,
            String adjustmentRequestId,
            String originalTransactionId,
            LedgerTransactionType transactionType,
            BigDecimal amount,
            BigDecimal runningBalance,
            String description,
            String createdByAccountId,
            String approvedByAccountId,
            Instant createdAt
    ) {
        return computeRecordHash(
                previousHash,
                transactionId,
                clanId,
                eventId,
                adjustmentRequestId,
                null,
                originalTransactionId,
                transactionType,
                amount,
                runningBalance,
                description,
                createdByAccountId,
                approvedByAccountId,
                createdAt
        );
    }

    /**
     * Computes a 64-character lowercase hexadecimal SHA-256 hash chained to {@code previousHash},
     * including both {@code adjustmentRequestId} (US-59) and {@code disbursementRequestId} (US-60).
     */
    public String computeRecordHash(
            String previousHash,
            String transactionId,
            String clanId,
            String eventId,
            String adjustmentRequestId,
            String disbursementRequestId,
            String originalTransactionId,
            LedgerTransactionType transactionType,
            BigDecimal amount,
            BigDecimal runningBalance,
            String description,
            String createdByAccountId,
            String approvedByAccountId,
            Instant createdAt
    ) {
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(clanId, "clanId must not be null");
        Objects.requireNonNull(transactionType, "transactionType must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(runningBalance, "runningBalance must not be null");
        Objects.requireNonNull(createdByAccountId, "createdByAccountId must not be null");
        Objects.requireNonNull(approvedByAccountId, "approvedByAccountId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        String normalizedPrevHash = (previousHash == null || previousHash.isBlank())
                ? GENESIS_HASH
                : previousHash.trim();

        // PostgreSQL TIMESTAMPTZ persists up to microseconds; truncate so DB round-trips match identically.
        String normalizedTimestamp = createdAt.truncatedTo(ChronoUnit.MICROS).toString();

        String canonicalPayload = String.join("|",
                normalizedPrevHash,
                transactionId,
                clanId,
                nullToEmpty(eventId),
                nullToEmpty(adjustmentRequestId),
                nullToEmpty(disbursementRequestId),
                nullToEmpty(originalTransactionId),
                transactionType.name(),
                formatMonetaryAmount(amount),
                formatMonetaryAmount(runningBalance),
                nullToEmpty(description),
                createdByAccountId,
                approvedByAccountId,
                normalizedTimestamp
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    /**
     * Verifies whether an {@link ImmutableLedger} entry matches its expected SHA-256 chain hash
     * and, when populated on the entity, its stored {@code previousHash} pointer.
     */
    public boolean verifyRecordHash(ImmutableLedger entry, String previousHash) {
        if (entry == null
                || entry.getRecordHash() == null
                || entry.getTransactionId() == null
                || entry.getClanId() == null
                || entry.getTransactionType() == null
                || entry.getAmount() == null
                || entry.getRunningBalance() == null
                || entry.getCreatedByAccountId() == null
                || entry.getApprovedByAccountId() == null
                || entry.getCreatedAt() == null) {
            return false;
        }

        String expectedPrevHash = normalizePreviousHash(previousHash);
        if (entry.getPreviousHash() != null && !entry.getPreviousHash().isBlank()
                && !constantTimeEquals(expectedPrevHash, entry.getPreviousHash().trim())) {
            return false;
        }

        String expectedHash = computeRecordHash(
                expectedPrevHash,
                entry.getTransactionId(),
                entry.getClanId(),
                entry.getEventId(),
                entry.getAdjustmentRequestId(),
                entry.getDisbursementRequestId(),
                entry.getOriginalTransactionId(),
                entry.getTransactionType(),
                entry.getAmount(),
                entry.getRunningBalance(),
                entry.getDescription(),
                entry.getCreatedByAccountId(),
                entry.getApprovedByAccountId(),
                entry.getCreatedAt()
        );

        return constantTimeEquals(expectedHash, entry.getRecordHash().trim());
    }

    /**
     * Walks a chronologically ordered ledger chain for a single clan and validates:
     * <ol>
     *     <li>Genesis anchor ({@code GENESIS}) and sequential {@code previousHash} linking,</li>
     *     <li>Deterministic SHA-256 {@code recordHash} matching every column payload, and</li>
     *     <li>Sequential {@code runningBalance} continuity between consecutive entries.</li>
     * </ol>
     */
    public ChainVerificationResult verifyChain(List<ImmutableLedger> orderedEntries) {
        if (orderedEntries == null || orderedEntries.isEmpty()) {
            return new ChainVerificationResult(true, 0, 0, List.of());
        }

        List<BrokenLinkDetail> brokenLinks = new ArrayList<>();
        String expectedPrevHash = GENESIS_HASH;
        BigDecimal previousBalance = null;
        int verifiedCount = 0;

        for (int i = 0; i < orderedEntries.size(); i++) {
            ImmutableLedger entry = orderedEntries.get(i);
            if (entry == null
                    || entry.getTransactionId() == null
                    || entry.getClanId() == null
                    || entry.getTransactionType() == null
                    || entry.getAmount() == null
                    || entry.getRunningBalance() == null
                    || entry.getCreatedByAccountId() == null
                    || entry.getApprovedByAccountId() == null
                    || entry.getCreatedAt() == null) {
                brokenLinks.add(new BrokenLinkDetail(
                        entry != null ? entry.getTransactionId() : null,
                        i,
                        "Thiếu dữ liệu bắt buộc trên bản ghi sổ cái để xác minh SHA-256",
                        expectedPrevHash,
                        entry != null ? entry.getPreviousHash() : null,
                        null,
                        entry != null ? entry.getRecordHash() : null
                ));
                if (entry != null && entry.getRecordHash() != null && !entry.getRecordHash().isBlank()) {
                    expectedPrevHash = entry.getRecordHash().trim();
                }
                if (entry != null && entry.getRunningBalance() != null) {
                    previousBalance = entry.getRunningBalance();
                }
                continue;
            }

            boolean entryValid = true;
            String actualPrevHash = entry.getPreviousHash() != null ? entry.getPreviousHash().trim() : null;
            String actualRecordHash = entry.getRecordHash() != null ? entry.getRecordHash().trim() : null;

            String expectedRecordHash = computeRecordHash(
                    expectedPrevHash,
                    entry.getTransactionId(),
                    entry.getClanId(),
                    entry.getEventId(),
                    entry.getAdjustmentRequestId(),
                    entry.getDisbursementRequestId(),
                    entry.getOriginalTransactionId(),
                    entry.getTransactionType(),
                    entry.getAmount(),
                    entry.getRunningBalance(),
                    entry.getDescription(),
                    entry.getCreatedByAccountId(),
                    entry.getApprovedByAccountId(),
                    entry.getCreatedAt()
            );

            if (actualPrevHash == null || !constantTimeEquals(expectedPrevHash, actualPrevHash)) {
                brokenLinks.add(new BrokenLinkDetail(
                        entry.getTransactionId(),
                        i,
                        "Liên kết previousHash không khớp với bản ghi liền trước",
                        expectedPrevHash,
                        actualPrevHash,
                        expectedRecordHash,
                        actualRecordHash
                ));
                entryValid = false;
            }

            if (actualRecordHash == null || !constantTimeEquals(expectedRecordHash, actualRecordHash)) {
                brokenLinks.add(new BrokenLinkDetail(
                        entry.getTransactionId(),
                        i,
                        "Mã băm SHA-256 (recordHash) không khớp với dữ liệu giao dịch",
                        expectedPrevHash,
                        actualPrevHash,
                        expectedRecordHash,
                        actualRecordHash
                ));
                entryValid = false;
            }

            if (i > 0 && previousBalance != null) {
                BigDecimal expectedBalance = previousBalance.add(entry.getAmount()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal actualBalance = entry.getRunningBalance().setScale(2, RoundingMode.HALF_UP);
                if (actualBalance.compareTo(expectedBalance) != 0) {
                    brokenLinks.add(new BrokenLinkDetail(
                            entry.getTransactionId(),
                            i,
                            "Số dư lũy kế (runningBalance) không khớp phép cộng liên tiếp ("
                                    + expectedBalance.toPlainString() + " != " + actualBalance.toPlainString() + ")",
                            expectedPrevHash,
                            actualPrevHash,
                            expectedRecordHash,
                            actualRecordHash
                    ));
                    entryValid = false;
                }
            }

            if (entryValid) {
                verifiedCount++;
            }

            expectedPrevHash = (actualRecordHash != null && !actualRecordHash.isBlank())
                    ? actualRecordHash
                    : expectedRecordHash;
            previousBalance = entry.getRunningBalance();
        }

        return new ChainVerificationResult(
                brokenLinks.isEmpty(),
                orderedEntries.size(),
                verifiedCount,
                List.copyOf(brokenLinks)
        );
    }

    public record ChainVerificationResult(
            boolean valid,
            int totalEntries,
            int verifiedEntries,
            List<BrokenLinkDetail> brokenLinks
    ) {
    }

    public record BrokenLinkDetail(
            String transactionId,
            int chainIndex,
            String reason,
            String expectedPreviousHash,
            String actualPreviousHash,
            String expectedRecordHash,
            String actualRecordHash
    ) {
    }

    private static String normalizePreviousHash(String previousHash) {
        return (previousHash == null || previousHash.isBlank()) ? GENESIS_HASH : previousHash.trim();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatMonetaryAmount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
