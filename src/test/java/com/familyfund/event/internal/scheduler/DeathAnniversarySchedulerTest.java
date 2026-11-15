package com.familyfund.event.internal.scheduler;

import com.familyfund.event.api.DeathAnniversaryService;
import com.familyfund.event.internal.anniversary.DeathAnniversaryProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link DeathAnniversaryScheduler} (US-40): the cron entry point only delegates, and never lets a
 * failure escape into the scheduler thread.
 */
@ExtendWith(MockitoExtension.class)
class DeathAnniversarySchedulerTest {

    @Mock
    private DeathAnniversaryService deathAnniversaryService;

    @InjectMocks
    private DeathAnniversaryScheduler scheduler;

    @Test
    @DisplayName("the @Scheduled cron must be a parseable SIX-field Spring expression")
    void cronExpressionIsValid() throws Exception {
        Scheduled scheduled = DeathAnniversaryScheduler.class
                .getMethod("sendDailyDeathAnniversaryReminders")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        // Spring rejects 5-field Unix crons at startup ("must consist of 6 fields"); catch that in a unit test
        // instead of on the first bootRun.
        assertThat(scheduled.cron().split("\s+")).hasSize(6);
        assertThat(CronExpression.parse(DeathAnniversaryProperties.DEFAULT_CRON)).isNotNull();
        assertThat(scheduled.zone()).isNotBlank();
    }

    @Test
    @DisplayName("the scheduled pass delegates to the service with no fixed date (today in the clan zone)")
    void delegatesToService() {
        when(deathAnniversaryService.scanAndSendReminders(isNull())).thenReturn(3);

        scheduler.sendDailyDeathAnniversaryReminders();

        verify(deathAnniversaryService).scanAndSendReminders(isNull());
    }

    @Test
    @DisplayName("a failing pass is swallowed so the scheduled task survives")
    void swallowsFailure() {
        when(deathAnniversaryService.scanAndSendReminders(isNull()))
                .thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> scheduler.sendDailyDeathAnniversaryReminders()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("one trigger runs exactly one pass")
    void onePassPerTrigger() {
        when(deathAnniversaryService.scanAndSendReminders(isNull())).thenReturn(0);

        scheduler.sendDailyDeathAnniversaryReminders();

        verify(deathAnniversaryService, times(1)).scanAndSendReminders(isNull());
    }
}
