package com.familyfund.finance.internal.service;

import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.response.LedgerIntegrityReportResponse;
import com.familyfund.finance.internal.entity.ImmutableLedger;
import com.familyfund.finance.internal.repository.ImmutableLedgerRepository;
import com.familyfund.finance.internal.util.ImmutableLedgerHashCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImmutableLedgerIntegrityServiceImplTest {

    private static final String CLAN_ID = "CLN_INTEGRITY";

    @Mock
    private ImmutableLedgerRepository immutableLedgerRepository;

    @Spy
    private ImmutableLedgerHashCalculator ledgerHashCalculator = new ImmutableLedgerHashCalculator();

    @InjectMocks
    private ImmutableLedgerIntegrityServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Trả về valid=true, totalEntries=0 khi Sổ cái dòng họ chưa có giao dịch nào")
    void verifyCurrentClanChain_emptyLedger_returnsValid() {
        when(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(CLAN_ID))
                .thenReturn(List.of());

        LedgerIntegrityReportResponse report = service.verifyCurrentClanChain();

        assertThat(report.clanId()).isEqualTo(CLAN_ID);
        assertThat(report.valid()).isTrue();
        assertThat(report.totalEntries()).isZero();
        assertThat(report.verifiedEntries()).isZero();
        assertThat(report.genesisAnchor()).isEqualTo("GENESIS");
        assertThat(report.latestTransactionId()).isNull();
        assertThat(report.latestRecordHash()).isNull();
        assertThat(report.brokenLinks()).isEmpty();
    }

    @Test
    @DisplayName("Trả về valid=true khi chuỗi 2 giao dịch nối tiếp đúng SHA-256 và phát hiện khi bị sửa")
    void verifyCurrentClanChain_validAndTamperedChains() {
        Instant t1 = Instant.parse("2026-11-23T01:00:00.123456Z");
        Instant t2 = Instant.parse("2026-11-23T01:05:00.123456Z");

        String h1 = ledgerHashCalculator.computeRecordHash(
                "GENESIS", "LDG_01", CLAN_ID, "EVT_1", null, null, null,
                LedgerTransactionType.SurplusTransfer, new BigDecimal("80000000.00"), new BigDecimal("80000000.00"),
                "Kết dư", "ACC_1", "ACC_H", t1
        );
        ImmutableLedger e1 = ImmutableLedger.builder()
                .transactionId("LDG_01").clanId(CLAN_ID).eventId("EVT_1")
                .transactionType(LedgerTransactionType.SurplusTransfer)
                .amount(new BigDecimal("80000000.00")).runningBalance(new BigDecimal("80000000.00"))
                .description("Kết dư").createdByAccountId("ACC_1").approvedByAccountId("ACC_H")
                .previousHash("GENESIS").recordHash(h1).createdAt(t1)
                .build();

        String h2 = ledgerHashCalculator.computeRecordHash(
                h1, "LDG_02", CLAN_ID, null, null, "CFD_1", null,
                LedgerTransactionType.CommonFundDisbursement, new BigDecimal("-30000000.00"), new BigDecimal("50000000.00"),
                "Chi tu sửa", "ACC_1", "ACC_H", t2
        );
        ImmutableLedger e2 = ImmutableLedger.builder()
                .transactionId("LDG_02").clanId(CLAN_ID).disbursementRequestId("CFD_1")
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(new BigDecimal("-30000000.00")).runningBalance(new BigDecimal("50000000.00"))
                .description("Chi tu sửa").createdByAccountId("ACC_1").approvedByAccountId("ACC_H")
                .previousHash(h1).recordHash(h2).createdAt(t2)
                .build();

        when(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(CLAN_ID))
                .thenReturn(List.of(e1, e2));

        LedgerIntegrityReportResponse validReport = service.verifyCurrentClanChain();
        assertThat(validReport.valid()).isTrue();
        assertThat(validReport.totalEntries()).isEqualTo(2);
        assertThat(validReport.verifiedEntries()).isEqualTo(2);
        assertThat(validReport.latestTransactionId()).isEqualTo("LDG_02");
        assertThat(validReport.latestRecordHash()).isEqualTo(h2);
        assertThat(validReport.brokenLinks()).isEmpty();

        // Giả mạo e2 bằng cách đổi description
        ImmutableLedger e2Tampered = ImmutableLedger.builder()
                .transactionId("LDG_02").clanId(CLAN_ID).disbursementRequestId("CFD_1")
                .transactionType(LedgerTransactionType.CommonFundDisbursement)
                .amount(new BigDecimal("-30000000.00")).runningBalance(new BigDecimal("50000000.00"))
                .description("Chi sai mục đích").createdByAccountId("ACC_1").approvedByAccountId("ACC_H")
                .previousHash(h1).recordHash(h2).createdAt(t2)
                .build();
        when(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(CLAN_ID))
                .thenReturn(List.of(e1, e2Tampered));

        LedgerIntegrityReportResponse brokenReport = service.verifyCurrentClanChain();
        assertThat(brokenReport.valid()).isFalse();
        assertThat(brokenReport.verifiedEntries()).isEqualTo(1);
        assertThat(brokenReport.brokenLinks()).hasSize(1);
        assertThat(brokenReport.brokenLinks().getFirst().transactionId()).isEqualTo("LDG_02");
    }

    @Test
    @DisplayName("Tự động dùng DEFAULT_CLAN_ID (CLAN_DEFAULT) khi chưa đặt TenantContext và ném BusinessRuleException nếu MockedStatic trả về null")
    void verifyCurrentClanChain_defaultClanFallbackAndMissingClanGuard() {
        TenantContext.clear();
        when(immutableLedgerRepository.findByClanIdOrderByCreatedAtAscTransactionIdAsc(TenantContext.DEFAULT_CLAN_ID))
                .thenReturn(List.of());

        LedgerIntegrityReportResponse defaultReport = service.verifyCurrentClanChain();
        assertThat(defaultReport.clanId()).isEqualTo(TenantContext.DEFAULT_CLAN_ID);
        assertThat(defaultReport.valid()).isTrue();

        try (var mockedTenant = org.mockito.Mockito.mockStatic(TenantContext.class)) {
            mockedTenant.when(TenantContext::getClanId).thenReturn("   ");
            assertThatThrownBy(() -> service.verifyCurrentClanChain())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("clanId");
        }
    }
}
