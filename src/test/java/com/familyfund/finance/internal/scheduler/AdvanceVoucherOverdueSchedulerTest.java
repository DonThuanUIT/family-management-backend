package com.familyfund.finance.internal.scheduler;

import com.familyfund.finance.api.AdvanceVoucherOverdueService;
import com.familyfund.finance.api.dto.OverdueScanSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvanceVoucherOverdueSchedulerTest {

    @Mock
    private AdvanceVoucherOverdueService advanceVoucherOverdueService;

    @InjectMocks
    private AdvanceVoucherOverdueScheduler scheduler;

    @Test
    @DisplayName("US-48: Scheduler kích hoạt service quét và ghi nhận kết quả")
    void scanAndProcessOverdueAdvanceVouchers_invokesService() {
        when(advanceVoucherOverdueService.scanAndProcessOverdueVouchers(any()))
                .thenReturn(new OverdueScanSummary(5, 3, 6, Instant.now()));

        assertThatCode(() -> scheduler.scanAndProcessOverdueAdvanceVouchers())
                .doesNotThrowAnyException();

        verify(advanceVoucherOverdueService).scanAndProcessOverdueVouchers(any());
    }

    @Test
    @DisplayName("US-48: Scheduler bắt và nuốt ngoại lệ runtime để không làm đứt vòng lặp scheduled task")
    void scanAndProcessOverdueAdvanceVouchers_handlesExceptionGracefully() {
        when(advanceVoucherOverdueService.scanAndProcessOverdueVouchers(any()))
                .thenThrow(new RuntimeException("Database timeout during scheduled pass"));

        assertThatCode(() -> scheduler.scanAndProcessOverdueAdvanceVouchers())
                .doesNotThrowAnyException();

        verify(advanceVoucherOverdueService).scanAndProcessOverdueVouchers(any());
    }
}
