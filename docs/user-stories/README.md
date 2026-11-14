# Danh Mục Đặc Tả Kỹ Thuật User Stories (`docs/user-stories/`)

Thư mục này lưu trữ toàn bộ các tài liệu đặc tả kỹ thuật chi tiết, thiết kế kiến trúc, mô hình dữ liệu, sequence diagram và hướng dẫn kiểm thử End-to-End cho từng User Story (US) trong dự án **Family Management Backend** (`familyfund-api`).

---

## 📌 Danh Sách User Stories

| Mã US | Tên Tính Năng | Bounded Context | Trạng Thái | File Đặc Tả Kỹ Thuật |
| :---: | :--- | :--- | :---: | :--- |
| **US-01** | **Đăng Ký Tài Khoản Người Khởi Tạo & Tạo Cây Gia Phả Mới** | `com.familyfund.identity` & `genealogy` | ✅ **Đã hoàn thành** | [`US-01-creator-registration-and-clan-creation.md`](./US-01-creator-registration-and-clan-creation.md) |
| **US-03** | **Tạo Hồ Sơ Thành Viên Gia Phả, Chống Trùng & Phân Quyền Theo Chi/Nhánh** | `com.familyfund.genealogy` | ✅ **Đã hoàn thành** | [`US-03-create-member-profile.md`](./US-03-create-member-profile.md) |
| **US-04** | **Quản Lý Chi Phái, Phân Nhánh & Cấu Trúc Phân Cấp Gia Phả** | `com.familyfund.genealogy` | ✅ **Đã hoàn thành** | [`US-04-child-branches-and-hierarchy.md`](./US-04-child-branches-and-hierarchy.md) |
| **US-06** | **Đăng Ký Tham Dự Sự Kiện & Thống Kê Cỗ Bàn** | `com.familyfund.event` | ✅ **Đã hoàn thành** | [`US-06-event-attendance-registration.md`](./US-06-event-attendance-registration.md) |
| **US-07** | **Khởi Tạo Hạng Mục Công Việc & Phân Công Nhiệm Vụ Sự Kiện** | `com.familyfund.event` | ✅ **Đã hoàn thành** | [`US-07-task-and-advance-planning.md`](./US-07-task-and-advance-planning.md) |
| **US-08** | **Cảnh Báo Tiến Độ Công Việc Tự Động (Background Worker 24h)** | `com.familyfund.event` | ✅ **Đã hoàn thành** | [`US-08-task-deadline-progress-warning.md`](./US-08-task-deadline-progress-warning.md) |
| **US-09** | **Ghi Nhận Đóng Góp Tiền Mặt/Chuyển Khoản & Cấp Phiếu Thu Điện Tử** | `com.familyfund.contribution` | ✅ **Đã hoàn thành (Backend)** | [`US-09-cash-contribution-and-e-receipt.md`](./US-09-cash-contribution-and-e-receipt.md) |
| **US-10** | **Ghi Nhận Hiện Vật Cúng Tiến, Quy Đổi Giá Trị & Quản Lý Vòng Đời Tài Sản Dòng Họ (Backlog #46)** | `com.familyfund.contribution` | ✅ **Đã hoàn thành (Backend)** | [`US-10-in-kind-contribution-and-clan-assets.md`](./US-10-in-kind-contribution-and-clan-assets.md) |
| **US-10 (Auth)** | **Cấu Hình JWT Token, Claims & Middleware Gán Quyền ROLE_CLAN_HEAD** | `com.familyfund.core` & `identity` | ✅ **Đã hoàn thành** | [`US-10-jwt-authentication-and-claims.md`](./US-10-jwt-authentication-and-claims.md) |
| **US-16** | **Tạo Mã Mời (Invite Code) Chung Của Gia Phả** | `com.familyfund.identity` | ✅ **Đã hoàn thành** | [`US-16-general-clan-invite.md`](./US-16-general-clan-invite.md) |
| **US-17** | **Đăng Ký Gia Nhập Dòng Họ Qua Mã Mời (Onboarding)** | `com.familyfund.identity` | ✅ **Đã hoàn thành** | [`US-17-branch-join-request.md`](./US-17-branch-join-request.md) |
| **US-18** | **Tạo Hồ Sơ Ảo (Vị Trí Lá) & Phát Hành Mã Liên Kết Vị Trí Dùng Một Lần** | `com.familyfund.identity` & `genealogy` | ✅ **Đã hoàn thành** | [`US-18-create-virtual-member-and-generate-position-link-code.md`](./US-18-create-virtual-member-and-generate-position-link-code.md) |
| **US-19** | **Nhập Mã Liên Kết Vị Trí Khi Đăng Ký & Tự Động Gia Nhập Đúng Chi/Nhánh** | `com.familyfund.identity` & `genealogy` | ✅ **Đã hoàn thành** | [`US-19-enter-position-link-code-and-join-automatically.md`](./US-19-enter-position-link-code-and-join-automatically.md) |
| **US-21** | **Khởi Tạo Sự Kiện & Phân Quyền Động (Contextual RBAC)** | `com.familyfund.event` | ✅ **Đã hoàn thành** | [`US-21-create-event-and-contextual-rbac.md`](./US-21-create-event-and-contextual-rbac.md) |
| **US-22** | **Thành Viên Xem, Cập Nhật Hồ Sơ Cá Nhân & Đổi Mật Khẩu** | `com.familyfund.identity` & `genealogy` | ✅ **Đã hoàn thành (Backend)** | [`US-22-member-profile-and-password-management.md`](./US-22-member-profile-and-password-management.md) |
| **US-25** | **Bảo Vệ Thông Tin Cá Nhân Thành Viên (Hybrid Privacy: RBAC + Quyền Tự Quyết)** | `com.familyfund.genealogy` | ✅ **Đã hoàn thành (Backend & Frontend)** | [`US-25-member-personal-data-privacy.md`](./US-25-member-personal-data-privacy.md) |
| **US-27** | **Ghi Nhận Tình Trạng Sống/Đã Mất, Ngày Mất & Tính Ngày Giỗ Âm Lịch** | `com.familyfund.genealogy` & `identity` | ✅ **Đã hoàn thành (Backend)** | [`US-27-record-deceased-status-and-lunar-death-anniversary.md`](./US-27-record-deceased-status-and-lunar-death-anniversary.md) |
| **US-35** | **Thông Báo Khi Mở Sự Kiện & Cập Nhật Lịch Trình** | `com.familyfund.event` | ✅ **Đã hoàn thành** | [`US-22-event-notification-and-reschedule.md`](./US-22-event-notification-and-reschedule.md) |
| **US-31** | **Nhập Hàng Loạt Thành Viên Từ File Excel/CSV** | `com.familyfund.genealogy` | ✅ **Đã hoàn thành (Backend & Frontend)** | [`US-31-bulk-import-members-excel-csv.md`](./US-31-bulk-import-members-excel-csv.md) |
| **US-41** | **Sửa Hoặc Hủy Đăng Ký Tham Dự Trước Khi Ban Tổ Chức Chốt** | `com.familyfund.event` | ✅ **Đã hoàn thành (Backend & Frontend)** | [`US-41-edit-or-cancel-registration-before-close.md`](./US-41-edit-or-cancel-registration-before-close.md) |
| **US-42** | **Bảng Tổng Hợp Tức Thời Cho BTC: Số Cá Nhân/Gia Đình, Tổng Suất Ăn, Phương Tiện** | `com.familyfund.event` | ✅ **Đã hoàn thành (Backend & Frontend)** | [`US-42-event-logistics-summary-and-export.md`](./US-42-event-logistics-summary-and-export.md) |
| **US-43** | **Cấu Hình Hạn Chót Đăng Ký, Sức Chứa Tối Đa & Bộ Đếm Ngược / Số Chỗ Còn Lại** | `com.familyfund.event` | ✅ **Đã hoàn thành (Backend & Frontend)** | [`US-43-registration-deadline-and-max-capacity.md`](./US-43-registration-deadline-and-max-capacity.md) |
| **US-32** | **Đính Kèm Ảnh Chân Dung, Tiểu Sử Và Tư Liệu Gia Sử Vào Hồ Sơ Thành Viên** | `com.familyfund.genealogy` & `core.storage` | **Backend đã kiểm chứng Supabase thật; đã bỏ local; chờ frontend** | [`US-32-member-portrait-biography-and-family-documents.md`](./US-32-member-portrait-biography-and-family-documents.md) |
| **US-26** | **Ghi Nhận Giao Dịch Vào Sổ Cái Bất Biến (Immutable Ledger)** | `com.familyfund.finance` | 📝 *Kế hoạch* | *Chưa khởi tạo* |
| **US-30** | **Tiếp Nhận Đóng Góp Quỹ Đa Hình (Tiền/Hiện Vật)** | `com.familyfund.contribution`| 📝 *Kế hoạch* | *Chưa khởi tạo* |
| **US-47** | **Leo Thang Cảnh Báo Công Việc Quá Hạn 12 Giờ (Overdue Escalation → Trưởng chi)** | `com.familyfund.event` | ✅ **Đã hoàn thành** | [`US-47-task-overdue-12h-escalation.md`](./US-47-task-overdue-12h-escalation.md) |
| **US-39** | **NotificationService Đa Nhóm (Events/Tasks/Finance), Push FCM & API Đánh Dấu Đã Đọc** | `com.familyfund.notification` | ✅ **Đã hoàn thành (Backend)** | [`US-39-notification-service-fcm-and-read-state.md`](./US-39-notification-service-fcm-and-read-state.md) |

---

## 🛠 Hướng Dẫn Dành Cho Team Khi Bổ Sung User Story Mới

Khi bạn hoàn thành việc phát triển một User Story mới hoặc cần viết đặc tả trước khi code, hãy làm theo 3 bước sau:

1. **Nhân bản file mẫu**:
   Sao chép file [`TEMPLATE-US.md`](./TEMPLATE-US.md) trong thư mục này thành tên mới theo quy tắc:
   ```text
   US-<Mã-Số>-<slug-tính-năng>.md
   ```
   *Ví dụ: `US-17-branch-join-request.md`, `US-25-member-personal-data-privacy.md`*

2. **Điền đầy đủ các mục trong template**:
   - Tiêu chí chấp nhận (Acceptance Criteria - AC).
   - Bounded Context sở hữu & các class liên quan.
   - Sơ đồ tuần tự tương tác (Mermaid sequence diagram).
   - Lệnh chạy test tự động (`.\gradlew.bat test --tests ...`).
   - Kịch bản cURL mẫu để test trên Swagger / Docker local.

3. **Cập nhật danh mục**:
   - Thêm dòng thông tin của US mới vào bảng danh sách phía trên của file này.
   - Cập nhật liên kết trong [`docs/project-status.md`](../project-status.md).
