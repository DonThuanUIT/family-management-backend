# US-37: Tổng Hợp Ngân Sách Sự Kiện & Cảnh Báo Khi Vượt Dự Toán (`getEventBudgetSummary`)

## 1. Mục Tiêu Nghiệp Vụ
Cung cấp API tổng hợp và đối chiếu ngân sách dự kiến của sự kiện (`events.estimated_budget`) với:
1. **Tổng định mức công việc (`totalTaskAdvanceBudget`):** Tổng `advance_budget` của toàn bộ hạng mục công việc (`event_tasks`) thuộc sự kiện.
2. **Tổng tiền đã tạm ứng (`totalAdvanceAmount`):** Tổng `advance_amount` của toàn bộ phiếu tạm ứng (`advance_vouchers`) thuộc các công việc của sự kiện.
3. **Tổng tiền thực chi (`totalActualExpenseAmount`):** Tổng `actual_expense_amount` của toàn bộ phiếu quyết toán (`expense_settlement_vouchers`, bao gồm cả các phiếu mới nộp đang `PendingReview` / `NeedsExplanation` để cảnh báo sớm), đồng thời tách riêng `verifiedActualExpenseAmount` cho các phiếu đã xác thực (`Valid`, `Settled`).

Hệ thống tự động bật cờ cảnh báo **`isOverBudget = true`** ngay khi tổng tạm ứng hoặc tổng thực chi vượt mức ngân sách dự kiến của sự kiện (`totalAdvanceAmount > estimatedBudget` hoặc `totalActualExpenseAmount > estimatedBudget`).

---

## 2. Thiết Kế Kiến Trúc & Chống Nhân Bản Dữ Liệu (Fan-out Prevention)
- **Tuân thủ Spring Modulith:** Toàn bộ truy vấn và xử lý nằm gọn trong bounded context `com.familyfund.event` (`EventController` $\rightarrow$ `EventService` $\rightarrow$ `EventTaskRepository`).
- **Chống nhân bản định mức khi JOIN (`1 Task -> N Advance Vouchers`):**
  - Một hạng mục công việc (`event_tasks`) có thể có nhiều phiếu tạm ứng (`advance_vouchers`), mỗi phiếu tạm ứng có tối đa 1 phiếu quyết toán (`expense_settlement_vouchers`).
  - Truy vấn `EventTaskRepository.aggregateTaskBudgetsByEventId(eventId)` thực hiện `GROUP BY et.task_id` để giữ nguyên 1 dòng trên mỗi công việc (`et.advance_budget` không bao giờ bị nhân bản), đồng thời tính `SUM(av.advance_amount)` và `SUM(esv.actual_expense_amount)` chính xác cho từng hạng mục.
  - Tầng `EventServiceImpl` cộng dồn các dòng công việc để tạo báo cáo tổng hợp toàn sự kiện kèm danh sách phân rã chi tiết `taskBreakdowns`.

---

## 3. Quy Tắc Tính Toán & Cờ Cảnh Báo

| Trường / Cờ | Công Thức / Điều Kiện |
| :--- | :--- |
| `isAdvanceOverEstimated` | `totalAdvanceAmount > estimatedBudget` |
| `isActualExpenseOverEstimated` | `totalActualExpenseAmount > estimatedBudget` |
| **`isOverBudget`** | `isAdvanceOverEstimated \|\| isActualExpenseOverEstimated` |
| `isTaskBudgetOverEstimated` | `totalTaskAdvanceBudget > estimatedBudget` |
| `hasTaskOverBudget` | Tồn tại ít nhất 1 công việc có `taskAdvanceAmount > advanceBudget` hoặc `taskActualExpense > advanceBudget` |
| `remainingBudgetByAdvance` | `estimatedBudget - totalAdvanceAmount` (số âm nếu vượt dự toán) |
| `remainingBudgetByActualExpense` | `estimatedBudget - totalActualExpenseAmount` (số âm nếu vượt dự toán) |
| `overrunAmount` | `max(0, max(totalAdvanceAmount, totalActualExpenseAmount) - estimatedBudget)` |
| `warningLevel` | `OVER_BUDGET` (khi `isOverBudget = true`) $\rightarrow$ `TASK_OVERRUN` (khi `hasTaskOverBudget = true`) $\rightarrow$ `TASK_PLAN_EXCEEDED` (khi `isTaskBudgetOverEstimated = true`) $\rightarrow$ `NORMAL` |

> **Lưu ý trường hợp `estimated_budget = 0`:** Nếu sự kiện có `estimated_budget = 0` nhưng phát sinh `totalAdvanceAmount > 0` hoặc `totalActualExpenseAmount > 0`, hệ thống vẫn bật `isOverBudget = true` và trả về tỷ lệ sử dụng `100.00%` (tránh lỗi chia cho 0).

---

## 4. Đặc Tả REST API

### Endpoint
- **Chính:** `GET /api/v1/events/{eventId}/budget-summary`
- **Alias tương thích:** `GET /api/v1/events/{eventId}/budget`
- **Phân quyền:** `@PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")` kèm kiểm tra cô lập đa dòng họ (`TenantContext.getClanId()`).

### Response Mẫu (`200 OK`)
```json
{
  "success": true,
  "message": "Tổng hợp ngân sách và kiểm tra cảnh báo vượt dự toán sự kiện thành công.",
  "data": {
    "eventId": "EVT_2026_01",
    "eventName": "Giỗ Tổ Xuân 2026",
    "clanId": "CLAN_DEFAULT",
    "eventStatus": "Ongoing",
    "estimatedBudget": 50000000.00,
    "totalTaskAdvanceBudget": 45000000.00,
    "totalAdvanceAmount": 52000000.00,
    "totalActualExpenseAmount": 48000000.00,
    "verifiedActualExpenseAmount": 30000000.00,
    "remainingBudgetByAdvance": -2000000.00,
    "remainingBudgetByActualExpense": 2000000.00,
    "overrunAmount": 2000000.00,
    "advanceUtilizationPercent": 104.00,
    "actualExpenseUtilizationPercent": 96.00,
    "isOverBudget": true,
    "isAdvanceOverEstimated": true,
    "isActualExpenseOverEstimated": false,
    "isTaskBudgetOverEstimated": false,
    "hasTaskOverBudget": true,
    "warningLevel": "OVER_BUDGET",
    "warningMessages": [
      "CẢNH BÁO: Tổng tiền đã tạm ứng (52000000.00 VNĐ) đã vượt ngân sách dự kiến của sự kiện (50000000.00 VNĐ) một khoản 2000000.00 VNĐ.",
      "CẢNH BÁO HẠNG MỤC: Có 1 hạng mục công việc có tổng tạm ứng hoặc thực chi vượt định mức công việc (advance_budget)."
    ],
    "totalTasksCount": 2,
    "totalAdvanceVouchersCount": 3,
    "totalSettlementVouchersCount": 2,
    "taskBreakdowns": [
      {
        "taskId": "TSK_001",
        "taskName": "Đặt mâm cỗ",
        "status": "InProgress",
        "assignedBranchId": "BR_ROOT",
        "assignedMemberId": "MEM_001",
        "deadline": "2026-04-10T10:00:00",
        "advanceBudget": 30000000.00,
        "totalAdvanceAmount": 35000000.00,
        "totalActualExpenseAmount": 32000000.00,
        "verifiedActualExpenseAmount": 30000000.00,
        "advanceVouchersCount": 2,
        "settlementVouchersCount": 1,
        "isTaskAdvanceOverBudget": true,
        "isTaskActualOverBudget": true,
        "isTaskOverBudget": true,
        "taskOverrunAmount": 5000000.00
      }
    ]
  }
}
```
