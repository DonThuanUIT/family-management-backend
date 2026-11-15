package com.familyfund.finance.internal.service;

import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.api.dto.OverdueScanSummary;
import com.familyfund.finance.internal.dto.OverdueProcessingResult;
import com.familyfund.finance.internal.entity.AdvanceVoucher;
import com.familyfund.finance.internal.repository.AdvanceVoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvanceVoucherOverdueServiceImplTest {

    @Mock
    private AdvanceVoucherRepository advanceVoucherRepository;

    @Mock
    private AdvanceVoucherOverdueProcessor overdueProcessor;

    @InjectMocks
    private AdvanceVoucherOverdueServiceImpl service;

    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-04-15T12:00:00Z");
    }

    @Test
    @DisplayName("US-48: scanAndProcessOverdueVouchers quét cross-clan và tổng hợp metrics chính xác")
    void scanAndProcessOverdueVouchers_success() {
        AdvanceVoucher v1 = AdvanceVoucher.builder()
                .advanceVoucherId("ADV_01")
                .taskId("TSK_01")
                .status(AdvanceVoucherStatus.ChuaTatToan)
                .settlementDeadline(now.minus(1, ChronoUnit.DAYS))
                .advanceAmount(new BigDecimal("1000000.00"))
                .build();

        AdvanceVoucher v2 = AdvanceVoucher.builder()
                .advanceVoucherId("ADV_02")
                .taskId("TSK_02")
                .status(AdvanceVoucherStatus.ChuaTatToan)
                .settlementDeadline(now.minus(2, ChronoUnit.DAYS))
                .advanceAmount(new BigDecimal("2000000.00"))
                .build();

        when(advanceVoucherRepository.findOverdueVouchers(now)).thenReturn(List.of(v1, v2));

        when(overdueProcessor.processOverdueVoucher("ADV_01", now))
                .thenReturn(new OverdueProcessingResult("ADV_01", true, 3));
        when(overdueProcessor.processOverdueVoucher("ADV_02", now))
                .thenReturn(new OverdueProcessingResult("ADV_02", true, 2));

        OverdueScanSummary summary = service.scanAndProcessOverdueVouchers(now);

        assertThat(summary.scannedCount()).isEqualTo(2);
        assertThat(summary.overdueTransitionedCount()).isEqualTo(2);
        assertThat(summary.notificationsSentCount()).isEqualTo(5);
        assertThat(summary.referenceTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("US-48: Lỗi khi xử lý một phiếu không làm gián đoạn các phiếu khác (Error Isolation)")
    void scanAndProcessOverdueVouchers_errorIsolation() {
        AdvanceVoucher v1 = AdvanceVoucher.builder()
                .advanceVoucherId("ADV_FAIL")
                .status(AdvanceVoucherStatus.ChuaTatToan)
                .settlementDeadline(now.minus(1, ChronoUnit.DAYS))
                .build();

        AdvanceVoucher v2 = AdvanceVoucher.builder()
                .advanceVoucherId("ADV_OK")
                .status(AdvanceVoucherStatus.ChuaTatToan)
                .settlementDeadline(now.minus(1, ChronoUnit.DAYS))
                .build();

        when(advanceVoucherRepository.findOverdueVouchers(now)).thenReturn(List.of(v1, v2));

        when(overdueProcessor.processOverdueVoucher("ADV_FAIL", now))
                .thenThrow(new RuntimeException("Simulated processing error"));
        when(overdueProcessor.processOverdueVoucher("ADV_OK", now))
                .thenReturn(new OverdueProcessingResult("ADV_OK", true, 2));

        OverdueScanSummary summary = service.scanAndProcessOverdueVouchers(now);

        assertThat(summary.scannedCount()).isEqualTo(2);
        assertThat(summary.overdueTransitionedCount()).isEqualTo(1);
        assertThat(summary.notificationsSentCount()).isEqualTo(2);
        verify(overdueProcessor).processOverdueVoucher("ADV_OK", now);
    }

    @Test
    @DisplayName("US-48: scanAndProcessOverdueVouchersForClan quét giới hạn trong clan chỉ định")
    void scanAndProcessOverdueVouchersForClan_success() {
        AdvanceVoucher v = AdvanceVoucher.builder()
                .advanceVoucherId("ADV_CLAN_01")
                .clanId("CLAN_ALPHA")
                .status(AdvanceVoucherStatus.ChuaTatToan)
                .settlementDeadline(now.minus(1, ChronoUnit.DAYS))
                .build();

        when(advanceVoucherRepository.findOverdueVouchersByClanId(eq("CLAN_ALPHA"), eq(now)))
                .thenReturn(List.of(v));

        when(overdueProcessor.processOverdueVoucher("ADV_CLAN_01", now))
                .thenReturn(new OverdueProcessingResult("ADV_CLAN_01", true, 3));

        OverdueScanSummary summary = service.scanAndProcessOverdueVouchersForClan("CLAN_ALPHA", now);

        assertThat(summary.scannedCount()).isEqualTo(1);
        assertThat(summary.overdueTransitionedCount()).isEqualTo(1);
        assertThat(summary.notificationsSentCount()).isEqualTo(3);
    }
}
