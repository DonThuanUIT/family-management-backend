package com.familyfund.event.internal.service;

import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.internal.dto.response.EventBudgetSummaryResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.mapper.EventMapper;
import com.familyfund.event.internal.repository.EventNotificationRepository;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.event.internal.repository.EventTaskRepository.TaskBudgetAggregateProjection;
import com.familyfund.event.internal.util.EventIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit Tests cho tính năng tổng hợp ngân sách sự kiện và cảnh báo vượt dự toán (US-37).
 */
@ExtendWith(MockitoExtension.class)
class EventBudgetSummaryServiceTest {

    private static final String CLAN_ID = "CLAN_DEFAULT";
    private static final String EVENT_ID = "EVT-GIO-TO-2026";

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventPersonnelRepository eventPersonnelRepository;
    @Mock
    private EventNotificationRepository eventNotificationRepository;
    @Mock
    private EventMapper eventMapper;
    @Mock
    private EventIdGenerator eventIdGenerator;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private GenealogyQueryService genealogyQueryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EventTaskRepository eventTaskRepository;

    private EventServiceImpl eventService;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
        eventService = new EventServiceImpl(
                eventRepository,
                eventPersonnelRepository,
                eventNotificationRepository,
                eventMapper,
                eventIdGenerator,
                currentUserProvider,
                genealogyQueryService,
                eventPublisher,
                eventTaskRepository
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("US-37: Trong hạn mức ngân sách dự kiến -> isOverBudget = false, warningLevel = NORMAL")
    void shouldReturnNormalStatusWhenWithinEstimatedBudget() {
        Event event = buildEvent(new BigDecimal("50000000.00"));
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

        TaskBudgetAggregateProjection t1 = projection(
                "TSK-01", "Mua sắm lễ vật", "InProgress", "25000000.00", 1, "20000000.00", 1, "19500000.00", "19500000.00", 0
        );
        TaskBudgetAggregateProjection t2 = projection(
                "TSK-02", "Thuê rạp & âm thanh", "Completed", "15000000.00", 1, "10000000.00", 1, "8500000.00", "8500000.00", 0
        );
        when(eventTaskRepository.aggregateTaskBudgetsByEventId(EVENT_ID)).thenReturn(List.of(t1, t2));

        EventBudgetSummaryResponse summary = eventService.getEventBudgetSummary(EVENT_ID);

        assertNotNull(summary);
        assertEquals(0, new BigDecimal("50000000.00").compareTo(summary.estimatedBudget()));
        assertEquals(0, new BigDecimal("40000000.00").compareTo(summary.totalAdvanceBudget()));
        assertEquals(0, new BigDecimal("30000000.00").compareTo(summary.totalAdvanceAmount()));
        assertEquals(0, new BigDecimal("28000000.00").compareTo(summary.totalActualExpenseAmount()));
        assertEquals(0, new BigDecimal("10000000.00").compareTo(summary.unallocatedBudget()));
        assertEquals(0, new BigDecimal("20000000.00").compareTo(summary.remainingBudgetByAdvance()));
        assertEquals(0, new BigDecimal("22000000.00").compareTo(summary.remainingBudgetByActualExpense()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.overrunAmount()));
        assertEquals(0, new BigDecimal("60.00").compareTo(summary.advanceUtilizationPercent()));
        assertEquals(0, new BigDecimal("56.00").compareTo(summary.actualExpenseUtilizationPercent()));
        assertFalse(summary.isOverBudget());
        assertFalse(summary.isAdvanceOverEstimated());
        assertFalse(summary.isActualExpenseOverEstimated());
        assertEquals("NORMAL", summary.warningLevel());
        assertTrue(summary.warningMessages().isEmpty());
    }

    @Test
    @DisplayName("US-37: Tổng tạm ứng vượt ngân sách dự kiến -> bật ngay cờ isOverBudget = true & isAdvanceOverEstimated = true")
    void shouldTriggerWarningWhenTotalAdvanceExceedsEstimatedBudget() {
        Event event = buildEvent(new BigDecimal("50000000.00"));
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

        TaskBudgetAggregateProjection t1 = projection(
                "TSK-01", "Cỗ bàn chính lễ", "InProgress", "45000000.00", 2, "55000000.00", 0, "0.00", "0.00", 0
        );
        when(eventTaskRepository.aggregateTaskBudgetsByEventId(EVENT_ID)).thenReturn(List.of(t1));

        EventBudgetSummaryResponse summary = eventService.getEventBudgetSummary(EVENT_ID);

        assertTrue(summary.isOverBudget());
        assertTrue(summary.isAdvanceOverEstimated());
        assertFalse(summary.isActualExpenseOverEstimated());
        assertTrue(summary.hasTaskOverBudget());
        assertEquals(0, new BigDecimal("5000000.00").compareTo(summary.overrunAmount()));
        assertEquals(0, new BigDecimal("-5000000.00").compareTo(summary.remainingBudgetByAdvance()));
        assertEquals(0, new BigDecimal("110.00").compareTo(summary.advanceUtilizationPercent()));
        assertEquals("OVERRUN_WARNING", summary.warningLevel());
        assertFalse(summary.warningMessages().isEmpty());
    }

    @Test
    @DisplayName("US-37: Tổng thực chi vượt ngân sách dự kiến -> bật ngay cờ isOverBudget = true & isActualExpenseOverEstimated = true")
    void shouldTriggerWarningWhenTotalActualExpenseExceedsEstimatedBudget() {
        Event event = buildEvent(new BigDecimal("50000000.00"));
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

        TaskBudgetAggregateProjection t1 = projection(
                "TSK-01", "Cỗ bàn & hậu cần", "Completed", "48000000.00", 1, "45000000.00", 1, "53500000.00", "53500000.00", 0
        );
        when(eventTaskRepository.aggregateTaskBudgetsByEventId(EVENT_ID)).thenReturn(List.of(t1));

        EventBudgetSummaryResponse summary = eventService.getEventBudgetSummary(EVENT_ID);

        assertTrue(summary.isOverBudget());
        assertFalse(summary.isAdvanceOverEstimated());
        assertTrue(summary.isActualExpenseOverEstimated());
        assertEquals(0, new BigDecimal("3500000.00").compareTo(summary.overrunAmount()));
        assertEquals(0, new BigDecimal("-3500000.00").compareTo(summary.remainingBudgetByActualExpense()));
        assertEquals(0, new BigDecimal("107.00").compareTo(summary.actualExpenseUtilizationPercent()));
        assertEquals("OVERRUN_WARNING", summary.warningLevel());
    }

    @Test
    @DisplayName("US-37: Khi estimated_budget = 0 nhưng phát sinh tạm ứng/thực chi > 0 -> vẫn bật isOverBudget = true")
    void shouldTriggerOverBudgetWhenEstimatedBudgetIsZeroAndSpendingOccurs() {
        Event event = buildEvent(BigDecimal.ZERO);
        when(eventRepository.findByEventIdAndClanId(EVENT_ID, CLAN_ID)).thenReturn(Optional.of(event));

        TaskBudgetAggregateProjection t1 = projection(
                "TSK-01", "Chi phí phát sinh", "InProgress", "2000000.00", 1, "2000000.00", 0, "0.00", "0.00", 0
        );
        when(eventTaskRepository.aggregateTaskBudgetsByEventId(EVENT_ID)).thenReturn(List.of(t1));

        EventBudgetSummaryResponse summary = eventService.getEventBudgetSummary(EVENT_ID);

        assertTrue(summary.isOverBudget());
        assertTrue(summary.isAdvanceOverEstimated());
        assertEquals(0, new BigDecimal("2000000.00").compareTo(summary.overrunAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(summary.advanceUtilizationPercent()));
    }

    @Test
    @DisplayName("US-37: Báo lỗi 404 khi sự kiện không tồn tại hoặc thuộc dòng họ khác")
    void shouldThrowResourceNotFoundWhenEventDoesNotBelongToClan() {
        when(eventRepository.findByEventIdAndClanId("EVT-UNKNOWN", CLAN_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                eventService.getEventBudgetSummary("EVT-UNKNOWN")
        );
    }

    private static Event buildEvent(BigDecimal estimatedBudget) {
        return Event.builder()
                .eventId(EVENT_ID)
                .clanId(CLAN_ID)
                .eventName("Đại Lễ Giỗ Tổ 2026")
                .eventDatetime(Instant.parse("2026-03-28T08:00:00Z"))
                .location("Từ Đường Dòng Họ")
                .estimatedBudget(estimatedBudget)
                .status(EventStatus.Ongoing)
                .createdBy("ACC_HEAD")
                .build();
    }

    private static TaskBudgetAggregateProjection projection(
            String taskId,
            String taskName,
            String status,
            String advanceBudget,
            int advCount,
            String totalAdv,
            int setCount,
            String totalActual,
            String verifiedActual,
            int needsExplanationCount
    ) {
        return new TaskBudgetAggregateProjection() {
            @Override
            public String getTaskId() {
                return taskId;
            }

            @Override
            public String getTaskName() {
                return taskName;
            }

            @Override
            public String getTaskStatus() {
                return status;
            }

            @Override
            public String getAssignedMemberId() {
                return "MEM-01";
            }

            @Override
            public String getAssignedBranchId() {
                return "BR-ROOT";
            }

            @Override
            public BigDecimal getAdvanceBudget() {
                return new BigDecimal(advanceBudget);
            }

            @Override
            public Integer getAdvanceVoucherCount() {
                return advCount;
            }

            @Override
            public BigDecimal getTotalAdvanceAmount() {
                return new BigDecimal(totalAdv);
            }

            @Override
            public Integer getSettlementVoucherCount() {
                return setCount;
            }

            @Override
            public BigDecimal getTotalActualExpenseAmount() {
                return new BigDecimal(totalActual);
            }

            @Override
            public BigDecimal getVerifiedActualExpenseAmount() {
                return new BigDecimal(verifiedActual);
            }

            @Override
            public Integer getNeedsExplanationCount() {
                return needsExplanationCount;
            }
        };
    }
}
