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
import java.util.HexFormat;
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
     * Verifies whether an {@link ImmutableLedger} entry matches its expected SHA-256 chain hash.
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

        String expectedHash = computeRecordHash(
                previousHash,
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

        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                entry.getRecordHash().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String formatMonetaryAmount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
