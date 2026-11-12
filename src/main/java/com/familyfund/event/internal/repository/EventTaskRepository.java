package com.familyfund.event.internal.repository;

import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.entity.EventTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for `EventTask` (table `event_tasks`).
 */
@Repository
public interface EventTaskRepository extends JpaRepository<EventTask, String> {

    Optional<EventTask> findByTaskId(String taskId);

    Optional<EventTask> findByTaskIdAndEvent_ClanId(String taskId, String clanId);

    List<EventTask> findAllByEventIdOrderByDeadlineAsc(String eventId);

    List<EventTask> findAllByEvent_EventIdAndEvent_ClanIdOrderByDeadlineAsc(String eventId, String clanId);

    List<EventTask> findAllByEvent_EventIdAndEvent_ClanIdAndStatusOrderByDeadlineAsc(
            String eventId, String clanId, TaskStatus status);

    List<EventTask> findAllByEvent_EventIdAndEvent_ClanIdAndAssignedMemberIdOrderByDeadlineAsc(
            String eventId, String clanId, String assignedMemberId);

    List<EventTask> findAllByEvent_EventIdAndEvent_ClanIdAndAssignedBranchIdOrderByDeadlineAsc(
            String eventId, String clanId, String assignedBranchId);

    boolean existsByTaskId(String taskId);

    long countByEvent_EventIdAndEvent_ClanId(String eventId, String clanId);

    /**
     * Finds every unfinished task whose deadline falls inside the window {@code (now, threshold]}.
     *
     * <p>US-08 background worker query: it runs on the scheduler thread, i.e. <b>outside</b> any HTTP
     * request, so there is no {@code TenantContext} and no {@code clan_id} filter on purpose — the
     * scan must cover every clan of the multi-tenant schema. Callers are responsible for resolving
     * the owning clan from the loaded event before notifying.</p>
     *
     * <p>Tasks completed ({@code completedStatus}) are excluded, as are tasks without an assigned
     * member (nobody to notify).</p>
     *
     * @param now             reference instant; only strictly future deadlines are considered
     * @param threshold       upper bound of the warning window (typically {@code now + 24h})
     * @param completedStatus terminal status excluded from the scan
     * @return unfinished assigned tasks ordered by the nearest deadline first
     */
    @Query("""
            SELECT t FROM EventTask t
            WHERE t.status <> :completedStatus
              AND t.assignedMemberId IS NOT NULL
              AND t.deadline > :now
              AND t.deadline <= :threshold
            ORDER BY t.deadline ASC
            """)
    List<EventTask> findUnfinishedTasksDueWithin(
            @Param("now") Instant now,
            @Param("threshold") Instant threshold,
            @Param("completedStatus") TaskStatus completedStatus);

    /**
     * Projection chứa số liệu ngân sách dự kiến công việc (`advance_budget`),
     * tổng tạm ứng (`advance_amount`) và tổng thực chi (`actual_expense_amount`)
     * của từng hạng mục công việc thuộc sự kiện (US-37).
     */
    interface TaskBudgetAggregateProjection {
        String getTaskId();
        String getTaskName();
        String getTaskStatus();
        String getAssignedMemberId();
        String getAssignedBranchId();
        BigDecimal getAdvanceBudget();
        Integer getAdvanceVoucherCount();
        BigDecimal getTotalAdvanceAmount();
        Integer getSettlementVoucherCount();
        BigDecimal getTotalActualExpenseAmount();
        BigDecimal getVerifiedActualExpenseAmount();
        Integer getNeedsExplanationCount();
    }

    /**
     * Gom nhóm ngân sách công việc, tổng tạm ứng (`advance_vouchers`) và tổng thực chi (`expense_settlement_vouchers`)
     * theo từng hạng mục công việc (`task_id`) của sự kiện để ngăn ngừa lỗi cộng trùng `advance_budget` (US-37).
     */
    @Query(value = """
            SELECT
                et.task_id AS taskId,
                et.task_name AS taskName,
                et.status AS taskStatus,
                et.assigned_member_id AS assignedMemberId,
                et.assigned_branch_id AS assignedBranchId,
                COALESCE(et.advance_budget, 0) AS advanceBudget,
                CAST(COUNT(DISTINCT av.advance_voucher_id) AS integer) AS advanceVoucherCount,
                COALESCE(SUM(av.advance_amount), 0) AS totalAdvanceAmount,
                CAST(COUNT(DISTINCT esv.settlement_voucher_id) AS integer) AS settlementVoucherCount,
                COALESCE(SUM(esv.actual_expense_amount), 0) AS totalActualExpenseAmount,
                COALESCE(SUM(CASE WHEN esv.status IN ('Valid', 'Settled') THEN esv.actual_expense_amount ELSE 0 END), 0) AS verifiedActualExpenseAmount,
                CAST(COUNT(DISTINCT CASE WHEN esv.status = 'NeedsExplanation' THEN esv.settlement_voucher_id END) AS integer) AS needsExplanationCount
            FROM event_tasks et
            LEFT JOIN advance_vouchers av
                ON av.task_id = et.task_id
            LEFT JOIN expense_settlement_vouchers esv
                ON esv.advance_voucher_id = av.advance_voucher_id
            WHERE et.event_id = :eventId
            GROUP BY et.task_id, et.task_name, et.status, et.assigned_member_id, et.assigned_branch_id, et.advance_budget, et.deadline
            ORDER BY et.deadline ASC, et.task_id ASC
            """, nativeQuery = true)
    List<TaskBudgetAggregateProjection> aggregateTaskBudgetsByEventId(@Param("eventId") String eventId);
}
