package com.familyfund.event.internal.dto.response;

import com.familyfund.event.api.dto.EventStatus;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Response DTO tổng hợp và so sánh {@code estimated_budget} của sự kiện với:
 * <ul>
 *   <li>Tổng {@code advance_budget} (ngân sách dự kiến các hạng mục công việc)</li>
 *   <li>Tổng {@code advance_amount} (tổng tiền đã tạm ứng)</li>
 *   <li>Tổng {@code actual_expense_amount} (tổng tiền thực chi)</li>
 * </ul>
 * Trả về hệ thống cờ cảnh báo ngay khi tổng tạm ứng hoặc tổng thực chi vượt mức dự kiến (US-37).
 */
public record EventBudgetSummaryResponse(
        String eventId,
        String eventName,
        EventStatus eventStatus,
        BigDecimal estimatedBudget,
        BigDecimal totalAdvanceBudget,
        BigDecimal totalAdvanceAmount,
        BigDecimal totalActualExpenseAmount,
        BigDecimal verifiedActualExpenseAmount,
        BigDecimal unallocatedBudget,
        BigDecimal remainingBudgetByAdvance,
        BigDecimal remainingBudgetByActualExpense,
        BigDecimal overrunAmount,
        BigDecimal taskBudgetUtilizationPercent,
        BigDecimal advanceUtilizationPercent,
        BigDecimal actualExpenseUtilizationPercent,
        boolean isOverBudget,
        boolean isAdvanceOverEstimated,
        boolean isActualExpenseOverEstimated,
        boolean isTaskBudgetOverEstimated,
        boolean hasTaskOverBudget,
        String warningLevel,
        List<String> warningMessages,
        int totalTaskCount,
        int totalAdvanceVoucherCount,
        int totalSettlementVoucherCount,
        int needsExplanationVoucherCount,
        List<TaskBudgetBreakdownResponse> taskBreakdowns
) {
    public EventBudgetSummaryResponse {
        warningMessages = (warningMessages != null) ? List.copyOf(warningMessages) : Collections.emptyList();
        taskBreakdowns = (taskBreakdowns != null) ? List.copyOf(taskBreakdowns) : Collections.emptyList();
    }
}
