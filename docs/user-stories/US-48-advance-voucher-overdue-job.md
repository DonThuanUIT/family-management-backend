# Đặc Tả Kỹ Thuật & Hướng Dẫn Vận Hành [US-48]: Background Job Quét Phiếu Tạm Ứng Quá Hạn & Cảnh Báo Đồng Thời

Tài liệu này đặc tả toàn diện thiết kế kiến trúc, mô hình dữ liệu, các giải pháp kỹ thuật, cơ chế quét tự động định kỳ, luồng xử lý chuyển đổi trạng thái và quy trình kiểm thử End-to-End (E2E) cho tính năng **[US-48] Job quét các phiếu trong `ADVANCE_VOUCHER` đang ở trạng thái chưa quyết toán (`ChuaTatToan`) mà thời gian thực đã vượt quá `settlement_deadline`, tự động đổi trạng thái sang `QuaHan` ('Quá hạn quyết toán') và gửi cảnh báo đồng thời cho người nhận tiền và Ban tổ chức**.

---

## 1. Tổng Quan Nghiệp Vụ (User Story & Acceptance Criteria)

### Mô tả User Story
* **Mã User Story:** `US-48`
* **Đối tượng tác động & thụ hưởng (Actors):**
  * **Hệ thống (Background Scheduler):** Tác vụ nền tự động kích hoạt định kỳ để quét toàn bộ dữ liệu phiếu tạm ứng trên tất cả dòng họ (`TenantContext.clear()`).
  * **Người nhận tiền tạm ứng (Recipient):** Thành viên đang giữ tiền tạm ứng nhưng chưa nộp chứng từ quyết toán khi thời hạn cam kết đã qua.
  * **Ban tổ chức sự kiện (BTC):** Các nhân sự quản lý sự kiện (Trưởng BTC, Thành viên BTC, Thủ quỹ, Trưởng tộc, Quản trị viên) chịu trách nhiệm đôn đốc, thu hồi tạm ứng và duy trì kỷ luật ngân sách.
* **Mục tiêu (Goal):** Tự động phát hiện các khoản tạm ứng quá hạn thanh quyết toán, chuyển trạng thái minh bạch sang `QuaHan`, và phát tín hiệu cảnh báo đồng thời đến cả người chịu trách nhiệm trực tiếp (người nhận) và cấp quản lý (Ban tổ chức).
* **Giá trị mang lại (Value):**
  * Tối ưu hóa thu hồi công nợ nội bộ gia tộc, ngăn ngừa tình trạng chiếm dụng hoặc quên nộp chứng từ chi tiêu sau khi sự kiện kết thúc.
  * Giảm tải thao tác rà soát thủ công của Thủ quỹ và Trưởng ban tổ chức.
  * Đồng bộ hóa với phân hệ thông báo đa kênh (`US-39` Inbox + FCM push) phục vụ cảnh báo thời gian thực.
* **Mức độ ưu tiên:** Bắt buộc (Must-Have).

### Tiêu chí chấp nhận (Acceptance Criteria - AC)
1. **AC-01 (Điều kiện quét & nhận diện phiếu quá hạn):**
   * Chỉ quét các phiếu tạm ứng thỏa mãn đồng thời:
     * `status = 'ChuaTatToan'` (tiền đã được giao thực tế theo US-46).
     * `settlement_deadline < now()` (mốc thời gian hiện tại đã vượt quá hạn chót nộp chứng từ).
   * Các phiếu ở trạng thái `ChoGiaoTien` (chưa nhận tiền), `DaTatToan` (đã nộp hóa đơn/chứng từ xong), hoặc `DaHuy` (phiếu đã hủy) **tuyệt đối không** bị chuyển sang `QuaHan`.
2. **AC-02 (Chuyển đổi trạng thái nguyên tử & cập nhật Audit):**
   * Chuyển `status` từ `ChuaTatToan` sang `QuaHan`.
   * Cập nhật `overdue_at = now()` và `updated_at = now()`.
   * Giao dịch độc lập per-voucher (`@Transactional` cô lập theo từng phiếu): một phiếu bị lỗi thông báo hoặc dữ liệu không làm ảnh hưởng hay rollback các phiếu khác trong cùng lượt quét.
3. **AC-03 (Phát cảnh báo đồng thời cho người nhận tiền và Ban tổ chức):**
   * **Người nhận tiền (`recipientMemberId`):**
     * Tra cứu tài khoản đăng nhập tương ứng thông qua `IdentityRoleCommandService.findAccountByMemberId`.
     * Gửi thông báo loại `NotificationCategory.Finance`, tiêu đề `"Cảnh báo: Quá hạn quyết toán tạm ứng"`, nội dung ghi rõ mã phiếu, số tiền tạm ứng, tên công việc và hạn chót nộp chứng từ.
   * **Ban tổ chức sự kiện (BTC):**
     * Tra cứu danh sách nhân sự tổ chức thông qua `EventQueryService.findOrganizerMemberIds(eventId)`.
     * Bổ sung người lập phiếu (`createdByAccountId`) và Thủ quỹ giải ngân (`treasurerAccountId`).
     * Khử trùng lặp danh sách nhận (`Set<String> recipientAccountIds`) để đảm bảo mỗi tài khoản BTC chỉ nhận duy nhất một cảnh báo cho mỗi phiếu quá hạn.
     * Gửi thông báo loại `NotificationCategory.Finance`, tiêu đề `"Cảnh báo: Phiếu tạm ứng quá hạn quyết toán"`, nội dung nêu rõ họ tên người nhận và khoản nợ quá hạn.
4. **AC-04 (Vận hành Background Scheduler & Quản trị Manual):**
   * Background Scheduler `AdvanceVoucherOverdueScheduler` chạy định kỳ (mặc định mỗi 15 phút - 900.000 ms), được cấu hình qua thuộc tính `app.finance.advance-overdue.cron` hoặc `fixed-delay-ms`.
   * Cho phép bật/tắt an toàn thông qua thuộc tính `@ConditionalOnProperty(name = "app.finance.advance-overdue.enabled", havingValue = "true", matchIfMissing = true)`.
   * Cung cấp API quản trị cưỡng bức quét thủ công: `POST /api/v1/advances/scan-overdue` (dành cho `ROLE_CLAN_HEAD` và `ROLE_ADMIN`).
5. **AC-05 (Tuân thủ kiến trúc Spring Modulith):**
   * Phân hệ `finance` giao tiếp với `event`, `identity`, `genealogy` và `notification` thông qua các Public API contracts (`EventQueryService`, `IdentityRoleCommandService`, `GenealogyQueryService`, `NotificationService`).
   * Không xâm phạm các gói nội bộ (`internal`). Đảm bảo `ArchitectureTest.verifyModularity()` vượt qua 100%.

---

## 2. Thiết Kế Kiến Trúc & Ranh Giới Bounded Context (Architecture Design)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. TRIGGER & INVOCATION LAYER                                               │
│    • Background Scheduler: AdvanceVoucherOverdueScheduler                   │
│      - @Scheduled(fixedDelay = 900000)                                      │
│      - Feature Flag: app.finance.advance-overdue.enabled                    │
│    • Management API: POST /api/v1/advances/scan-overdue                     │
│      - Controller: AdvanceVoucherController                                 │
│      - RBAC: @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")              │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. COORDINATION & MULTI-TENANT SCAN LAYER                                   │
│    • Service: AdvanceVoucherOverdueServiceImpl                              │
│      - TenantContext.clear() (xóa ngữ cảnh dòng họ để quét toàn bộ hệ thống)│
│      - AdvanceVoucherRepository.findOverdueVouchers(Instant now)            │
│      - Điều phối vòng lặp xử lý với bắt lỗi độc lập                         │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. ISOLATED TRANSACTIONAL PROCESSOR                                         │
│    • Processor: AdvanceVoucherOverdueProcessor                              │
│      - Status Transition: ChuaTatToan -> QuaHan                             │
│      - Audit Timestamps: overdueAt = now, updatedAt = now                   │
│      - Idempotency & Concurrency: Check voucher status before update        │
└───────────────────┬─────────────────────────────────────┬───────────────────┘
                    │                                     │
                    ▼ (Cross-Module Invocations)          ▼
┌──────────────────────────────────────┐  ┌───────────────────────────────────┐
│ 4. STAKEHOLDER RESOLUTION CONTRACTS  │  │ 5. NOTIFICATION DELIVERY (US-39)  │
│    • EventQueryService (event::api): │  │    • NotificationService          │
│      - findTaskSummary(taskId)       │  │      (notification::api):         │
│      - findOrganizerMemberIds(evtId) │  │      - notify(recipientPayload)   │
│    • IdentityRoleCommandService:     │  │      - notify(organizerPayloads)  │
│      - findAccountByMemberId(memId)  │  │    • Channel: In-app Inbox + FCM  │
│      - findAccountsByMemberIds(ids)  │  │    • Category: Finance            │
│    • GenealogyQueryService:          │  │    • Reference: advanceVoucherId  │
│      - findMemberSummary(memId)      │  └───────────────────────────────────┘
└──────────────────────────────────────┘
```

---

## 3. Thiết Kế Cơ Sở Dữ Liệu (Database Migration & Entities)

### Migration Script Flyway: `V26__add_quahan_status_and_overdue_to_advance_vouchers.sql`
```sql
-- Thêm cột thời điểm quá hạn overdue_at vào bảng advance_vouchers
ALTER TABLE advance_vouchers
    ADD COLUMN IF NOT EXISTS overdue_at TIMESTAMPTZ;

-- Nâng cấp ràng buộc kiểm tra trạng thái chk_advance_vouchers_status
ALTER TABLE advance_vouchers
    DROP CONSTRAINT IF EXISTS chk_advance_vouchers_status;

ALTER TABLE advance_vouchers
    ADD CONSTRAINT chk_advance_vouchers_status
    CHECK (status IN (
        'ChoGiaoTien', 'PendingDisbursement',
        'ChuaTatToan', 'Undisbursed',
        'DaTatToan', 'Settled',
        'DaHuy', 'Cancelled',
        'QuaHan', 'Overdue'
    ));

-- Partial index tối ưu hóa cho tác vụ quét định kỳ các phiếu quá hạn
CREATE INDEX IF NOT EXISTS idx_advance_vouchers_overdue_scan
    ON advance_vouchers (settlement_deadline)
    WHERE status = 'ChuaTatToan';
```

---

## 4. Đặc Tả Chi Tiết API & Scheduler

### 4.1. Background Scheduler: `AdvanceVoucherOverdueScheduler`
* **Lịch trình mặc định:** `fixedDelay = 900000` (15 phút một lần).
* **Cấu hình Spring:**
  ```yaml
  app:
    finance:
      advance-overdue:
        enabled: true
        fixed-delay-ms: 900000
        initial-delay-ms: 60000
  ```

### 4.2. API Kích Hoạt Quét Thủ Công
* **HTTP Method & URL:** `POST /api/v1/advances/scan-overdue` (alias `POST /api/advances/scan-overdue`)
* **Quyền hạn (RBAC):** `ROLE_CLAN_HEAD`, `ROLE_ADMIN`
* **Response (200 OK):**
```json
{
  "success": true,
  "message": "Quét và xử lý phiếu tạm ứng quá hạn thành công",
  "data": {
    "scannedCount": 12,
    "overdueTransitionedCount": 3,
    "notificationsSentCount": 8,
    "referenceTime": "2026-11-15T09:30:00Z"
  }
}
```

---

## 5. Chiến Lược Kiểm Thử (Testing Strategy)

1. **Unit Tests:**
   * `AdvanceVoucherOverdueProcessorTest`: Kiểm tra chuyển trạng thái sang `QuaHan`, gán `overdueAt`, gửi đồng thời 2 luồng thông báo cho người nhận và danh sách BTC.
   * `AdvanceVoucherOverdueServiceImplTest`: Kiểm tra quét xuyên dòng họ (`TenantContext.clear()`), xử lý ngoại lệ cô lập theo từng voucher.
   * `AdvanceVoucherOverdueSchedulerTest`: Kiểm tra lời gọi scheduler đúng tham số thời gian.
2. **PostgreSQL Integration Test (`AdvanceVoucherOverdueIntegrationTest`):**
   * Kiểm thử E2E trên cơ sở dữ liệu thật với cấu trúc bảng Flyway V26.
   * Tạo 3 kịch bản: Phiếu quá hạn (`ChuaTatToan` + deadline quá hạn), Phiếu chưa giao tiền (`ChoGiaoTien` + deadline quá hạn), và Phiếu chưa đến hạn (`ChuaTatToan` + deadline tương lai).
   * Khẳng định chỉ duy nhất phiếu quá hạn chuyển sang `QuaHan`, các phiếu khác giữ nguyên, và bản ghi cảnh báo được ghi chính xác vào bảng `notifications`.
3. **Kiểm tra Modularity (`ArchitectureTest`):**
   * Đảm bảo tuân thủ tuyệt đối quy tắc đóng gói Spring Modulith giữa `finance` và các bounded context khác.
