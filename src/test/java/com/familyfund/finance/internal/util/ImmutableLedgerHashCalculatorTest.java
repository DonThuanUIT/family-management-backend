package com.familyfund.finance.internal.util;

import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ImmutableLedgerHashCalculatorTest {

    private final ImmutableLedgerHashCalculator calculator = new ImmutableLedgerHashCalculator();

    @Test
    @DisplayName("Sinh mã SHA-256 dài 64 ký tự hex chữ thường và nhất quán (deterministic)")
    void computeRecordHash_validAndDeterministic() {
        Instant timestamp = Instant.parse("2026-11-22T07:15:30.123456Z");
        String prevHash = "a".repeat(64);

        String hash1 = calculator.computeRecordHash(
                prevHash, "LDG_002", "CLN_01", "EVT_01", "LAR_001", "LDG_001",
                LedgerTransactionType.AdjustmentTransaction, new BigDecimal("-5000000.00"),
                new BigDecimal("165000000.00"), "Điều chỉnh hóa đơn",
                "ACC_TREASURER", "ACC_HEAD", timestamp
        );
        String hash2 = calculator.computeRecordHash(
                prevHash, "LDG_002", "CLN_01", "EVT_01", "LAR_001", "LDG_001",
                LedgerTransactionType.AdjustmentTransaction, new BigDecimal("-5000000.00"),
                new BigDecimal("165000000.00"), "Điều chỉnh hóa đơn",
                "ACC_TREASURER", "ACC_HEAD", timestamp
        );

        assertThat(hash1).matches("^[0-9a-f]{64}$");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Chuẩn hóa GENESIS khi previousHash null/blank, chuẩn hóa scale BigDecimal và microsecond Instant")
    void computeRecordHash_normalizationConsistency() {
        Instant nanoInstant = Instant.parse("2026-11-22T07:15:30.123456789Z");
        Instant microInstant = nanoInstant.truncatedTo(ChronoUnit.MICROS);

        String hashNullPrev = calculator.computeRecordHash(
                null, "LDG_001", "CLN_01", null, null, null,
                LedgerTransactionType.SurplusTransfer, new BigDecimal("5000000"),
                new BigDecimal("5000000"), "Kết dư",
                "ACC_TREAS", "ACC_HEAD", nanoInstant
        );
        String hashBlankPrev = calculator.computeRecordHash(
                "   ", "LDG_001", "CLN_01", null, null, null,
                LedgerTransactionType.SurplusTransfer, new BigDecimal("5000000.00"),
                new BigDecimal("5000000.00"), "Kết dư",
                "ACC_TREAS", "ACC_HEAD", microInstant
        );
        String hashGenesisPrev = calculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH, "LDG_001", "CLN_01", null, null, null,
                LedgerTransactionType.SurplusTransfer, new BigDecimal("5000000.00"),
                new BigDecimal("5000000.00"), "Kết dư",
                "ACC_TREAS", "ACC_HEAD", microInstant
        );

        assertThat(hashNullPrev).isEqualTo(hashBlankPrev).isEqualTo(hashGenesisPrev);
    }

    @Test
    @DisplayName("Phát hiện thay đổi (tamper detection) khi sửa bất kỳ trường trọng yếu nào")
    void verifyRecordHash_detectsTampering() {
        Instant ts = Instant.parse("2026-11-22T07:15:30.123456Z");
        String prevHash = "b".repeat(64);
        String validHash = calculator.computeRecordHash(
                prevHash, "LDG_003", "CLN_01", "EVT_01", "LAR_002", "LDG_001",
                LedgerTransactionType.AdjustmentTransaction, new BigDecimal("-1000000.00"),
                new BigDecimal("99000000.00"), "Bù trừ",
                "ACC_TREAS", "ACC_HEAD", ts
        );

        ImmutableLedger validEntry = ImmutableLedger.builder()
                .transactionId("LDG_003")
                .clanId("CLN_01")
                .eventId("EVT_01")
                .adjustmentRequestId("LAR_002")
                .originalTransactionId("LDG_001")
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("-1000000.00"))
                .runningBalance(new BigDecimal("99000000.00"))
                .description("Bù trừ")
                .createdByAccountId("ACC_TREAS")
                .approvedByAccountId("ACC_HEAD")
                .recordHash(validHash)
                .createdAt(ts)
                .build();

        assertThat(calculator.verifyRecordHash(validEntry, prevHash)).isTrue();
        assertThat(calculator.verifyRecordHash(validEntry, "c".repeat(64))).isFalse();

        ImmutableLedger tamperedAmount = ImmutableLedger.builder()
                .transactionId("LDG_003").clanId("CLN_01").eventId("EVT_01")
                .adjustmentRequestId("LAR_002").originalTransactionId("LDG_001")
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("-1000001.00")).runningBalance(new BigDecimal("99000000.00"))
                .description("Bù trừ").createdByAccountId("ACC_TREAS").approvedByAccountId("ACC_HEAD")
                .recordHash(validHash).createdAt(ts).build();
        assertThat(calculator.verifyRecordHash(tamperedAmount, prevHash)).isFalse();

        ImmutableLedger tamperedBalance = ImmutableLedger.builder()
                .transactionId("LDG_003").clanId("CLN_01").eventId("EVT_01")
                .adjustmentRequestId("LAR_002").originalTransactionId("LDG_001")
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("-1000000.00")).runningBalance(new BigDecimal("99000001.00"))
                .description("Bù trừ").createdByAccountId("ACC_TREAS").approvedByAccountId("ACC_HEAD")
                .recordHash(validHash).createdAt(ts).build();
        assertThat(calculator.verifyRecordHash(tamperedBalance, prevHash)).isFalse();

        ImmutableLedger tamperedDesc = ImmutableLedger.builder()
                .transactionId("LDG_003").clanId("CLN_01").eventId("EVT_01")
                .adjustmentRequestId("LAR_002").originalTransactionId("LDG_001")
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("-1000000.00")).runningBalance(new BigDecimal("99000000.00"))
                .description("Bù trừ đã bị sửa").createdByAccountId("ACC_TREAS").approvedByAccountId("ACC_HEAD")
                .recordHash(validHash).createdAt(ts).build();
        assertThat(calculator.verifyRecordHash(tamperedDesc, prevHash)).isFalse();

        ImmutableLedger tamperedApprover = ImmutableLedger.builder()
                .transactionId("LDG_003").clanId("CLN_01").eventId("EVT_01")
                .adjustmentRequestId("LAR_002").originalTransactionId("LDG_001")
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("-1000000.00")).runningBalance(new BigDecimal("99000000.00"))
                .description("Bù trừ").createdByAccountId("ACC_TREAS").approvedByAccountId("ACC_FAKE")
                .recordHash(validHash).createdAt(ts).build();
        assertThat(calculator.verifyRecordHash(tamperedApprover, prevHash)).isFalse();

        ImmutableLedger tamperedTime = ImmutableLedger.builder()
                .transactionId("LDG_003").clanId("CLN_01").eventId("EVT_01")
                .adjustmentRequestId("LAR_002").originalTransactionId("LDG_001")
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("-1000000.00")).runningBalance(new BigDecimal("99000000.00"))
                .description("Bù trừ").createdByAccountId("ACC_TREAS").approvedByAccountId("ACC_HEAD")
                .recordHash(validHash).createdAt(ts.plusSeconds(1)).build();
        assertThat(calculator.verifyRecordHash(tamperedTime, prevHash)).isFalse();

        assertThat(calculator.verifyRecordHash(null, prevHash)).isFalse();
    }

    @Test
    @DisplayName("US-60: Hỗ trợ CommonFundDisbursement và phát hiện giả mạo disbursementRequestId")
    void verifyRecordHash_disbursementRequestId_tamperDetection() {
        Instant ts = Instant.parse("2026-11-22T08:00:00.123456Z");
        String prevHash = "d".repeat(64);
        String validHash = calculator.computeRecordHash(
                prevHash, "LDG_CFD_01", "CLN_01", null, null, "CFD_001", null,
                LedgerTransactionType.CommonFundDisbursement, new BigDecimal("-45000000.00"),
                new BigDecimal("55000000.00"), "Chi tu sửa mái ngói Từ đường",
                "ACC_TREAS", "ACC_HEAD", ts
        );

        ImmutableLedger validDisbursement = ImmutableLedger.builder()
                .transactionId("LDG_CFD_01")
                .clanId("CLN_01")
                .disbursementRequestId("CFD_001")
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(new BigDecimal("-45000000.00"))
                .runningBalance(new BigDecimal("55000000.00"))
                .description("Chi tu sửa mái ngói Từ đường")
                .createdByAccountId("ACC_TREAS")
                .approvedByAccountId("ACC_HEAD")
                .recordHash(validHash)
                .createdAt(ts)
                .build();

        assertThat(calculator.verifyRecordHash(validDisbursement, prevHash)).isTrue();

        ImmutableLedger tamperedDisbursementRef = ImmutableLedger.builder()
                .transactionId("LDG_CFD_01")
                .clanId("CLN_01")
                .disbursementRequestId("CFD_999")
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(new BigDecimal("-45000000.00"))
                .runningBalance(new BigDecimal("55000000.00"))
                .description("Chi tu sửa mái ngói Từ đường")
                .createdByAccountId("ACC_TREAS")
                .approvedByAccountId("ACC_HEAD")
                .previousHash(prevHash)
                .recordHash(validHash)
                .createdAt(ts)
                .build();

        assertThat(calculator.verifyRecordHash(tamperedDisbursementRef, prevHash)).isFalse();

        ImmutableLedger tamperedPrevHashField = ImmutableLedger.builder()
                .transactionId("LDG_CFD_01")
                .clanId("CLN_01")
                .disbursementRequestId("CFD_001")
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(new BigDecimal("-45000000.00"))
                .runningBalance(new BigDecimal("55000000.00"))
                .description("Chi tu sửa mái ngói Từ đường")
                .createdByAccountId("ACC_TREAS")
                .approvedByAccountId("ACC_HEAD")
                .previousHash("e".repeat(64))
                .recordHash(validHash)
                .createdAt(ts)
                .build();

        assertThat(calculator.verifyRecordHash(tamperedPrevHashField, prevHash)).isFalse();
    }

    @Test
    @DisplayName("US-61: verifyChain xác minh toàn chuỗi từ GENESIS và phát hiện sai previousHash, recordHash hoặc runningBalance")
    void verifyChain_validatesMultiBlockChainAndDetectsBreaks() {
        assertThat(calculator.verifyChain(java.util.List.of()).valid()).isTrue();

        Instant t1 = Instant.parse("2026-11-22T07:00:00.123456Z");
        Instant t2 = t1.plusSeconds(60);
        Instant t3 = t2.plusSeconds(60);

        String h1 = calculator.computeRecordHash(
                ImmutableLedgerHashCalculator.GENESIS_HASH,
                "LDG_1", "CLN_01", "EVT_01", null, null, null,
                LedgerTransactionType.SurplusTransfer,
                new BigDecimal("100000000.00"), new BigDecimal("100000000.00"),
                "Kết dư", "ACC_1", "ACC_H", t1
        );
        ImmutableLedger b1 = ImmutableLedger.builder()
                .transactionId("LDG_1").clanId("CLN_01").eventId("EVT_01")
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("100000000.00")).runningBalance(new BigDecimal("100000000.00"))
                .description("Kết dư").createdByAccountId("ACC_1").approvedByAccountId("ACC_H")
                .previousHash(ImmutableLedgerHashCalculator.GENESIS_HASH).recordHash(h1).createdAt(t1)
                .build();

        String h2 = calculator.computeRecordHash(
                h1, "LDG_2", "CLN_01", "EVT_01", "LAR_1", null, "LDG_1",
                LedgerTransactionType.AdjustmentTransaction,
                new BigDecimal("-5000000.00"), new BigDecimal("95000000.00"),
                "Bù trừ", "ACC_1", "ACC_H", t2
        );
        ImmutableLedger b2 = ImmutableLedger.builder()
                .transactionId("LDG_2").clanId("CLN_01").eventId("EVT_01")
                .adjustmentRequestId("LAR_1").originalTransactionId("LDG_1")
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("-5000000.00")).runningBalance(new BigDecimal("95000000.00"))
                .description("Bù trừ").createdByAccountId("ACC_1").approvedByAccountId("ACC_H")
                .previousHash(h1).recordHash(h2).createdAt(t2)
                .build();

        String h3 = calculator.computeRecordHash(
                h2, "LDG_3", "CLN_01", null, null, "CFD_1", null,
                LedgerTransactionType.CommonFundDisbursement,
                new BigDecimal("-20000000.00"), new BigDecimal("75000000.00"),
                "Chi tu sửa", "ACC_1", "ACC_H", t3
        );
        ImmutableLedger b3 = ImmutableLedger.builder()
                .transactionId("LDG_3").clanId("CLN_01").disbursementRequestId("CFD_1")
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(new BigDecimal("-20000000.00")).runningBalance(new BigDecimal("75000000.00"))
                .description("Chi tu sửa").createdByAccountId("ACC_1").approvedByAccountId("ACC_H")
                .previousHash(h2).recordHash(h3).createdAt(t3)
                .build();

        ImmutableLedgerHashCalculator.ChainVerificationResult validResult =
                calculator.verifyChain(java.util.List.of(b1, b2, b3));
        assertThat(validResult.valid()).isTrue();
        assertThat(validResult.totalEntries()).isEqualTo(3);
        assertThat(validResult.verifiedEntries()).isEqualTo(3);
        assertThat(validResult.brokenLinks()).isEmpty();

        // Giả mạo số tiền trên b2 mà giữ nguyên hash cũ -> verifyChain phải phát hiện ngay tại LDG_2
        ImmutableLedger b2Tampered = ImmutableLedger.builder()
                .transactionId("LDG_2").clanId("CLN_01").eventId("EVT_01")
                .adjustmentRequestId("LAR_1").originalTransactionId("LDG_1")
                .transactionType(LedgerTransactionType.AdjustmentTransaction)
                .amount(new BigDecimal("-1000000.00")).runningBalance(new BigDecimal("95000000.00"))
                .description("Bù trừ").createdByAccountId("ACC_1").approvedByAccountId("ACC_H")
                .previousHash(h1).recordHash(h2).createdAt(t2)
                .build();

        ImmutableLedgerHashCalculator.ChainVerificationResult brokenResult =
                calculator.verifyChain(java.util.List.of(b1, b2Tampered, b3));
        assertThat(brokenResult.valid()).isFalse();
        assertThat(brokenResult.verifiedEntries()).isEqualTo(2);
        assertThat(brokenResult.brokenLinks()).anyMatch(link -> "LDG_2".equals(link.transactionId()));

        // Giả mạo runningBalance không khớp giữa b2 và b3 dù có hash khớp payload giả
        String h3BadBalance = calculator.computeRecordHash(
                h2, "LDG_3", "CLN_01", null, null, "CFD_1", null,
                LedgerTransactionType.CommonFundDisbursement,
                new BigDecimal("-20000000.00"), new BigDecimal("80000000.00"),
                "Chi tu sửa", "ACC_1", "ACC_H", t3
        );
        ImmutableLedger b3BadBalance = ImmutableLedger.builder()
                .transactionId("LDG_3").clanId("CLN_01").disbursementRequestId("CFD_1")
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(new BigDecimal("-20000000.00")).runningBalance(new BigDecimal("80000000.00"))
                .description("Chi tu sửa").createdByAccountId("ACC_1").approvedByAccountId("ACC_H")
                .previousHash(h2).recordHash(h3BadBalance).createdAt(t3)
                .build();

        ImmutableLedgerHashCalculator.ChainVerificationResult badBalanceResult =
                calculator.verifyChain(java.util.List.of(b1, b2, b3BadBalance));
        assertThat(badBalanceResult.valid()).isFalse();
        assertThat(badBalanceResult.brokenLinks()).anyMatch(link ->
                "LDG_3".equals(link.transactionId()) && link.reason().contains("runningBalance"));
    }
}
