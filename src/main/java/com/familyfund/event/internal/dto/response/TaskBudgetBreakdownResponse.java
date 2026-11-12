package com.familyfund.event.internal.dto.response;

import java.math.BigDecimal;

/**
 * DTO phản hồi tình hình sử dụng ngân sách ở cấp từng hạng mục công việc (`EVENT_TASK`) - US-37.
 */
public record TaskBudgetBreakdownResponse(
        String taskId,
        String taskName,
        String taskStatus,
        String assignedMemberId,
        String assignedBranchId,
        BigDecimal advanceBudget,
        BigDecimal advanceAmount,
        BigDecimal actualExpenseAmount,
        BigDecimal verifiedActualExpenseAmount,
        int advanceVoucherCount,
        int settlementVoucherCount,
        int needsExplanationCount,
        BigDecimal advanceVariance,
        BigDecimal actualExpenseVariance,
        boolean isAdvanceOverTaskBudget,
        boolean isActualExpenseOverTaskBudget,
        boolean isOverTaskBudget
) {
}
