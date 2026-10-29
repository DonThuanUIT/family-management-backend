# Danh Mục Đặc Tả Kỹ Thuật User Stories (`docs/user-stories/`)

Thư mục này lưu trữ toàn bộ các tài liệu đặc tả kỹ thuật chi tiết, thiết kế kiến trúc, mô hình dữ liệu, sequence diagram và hướng dẫn kiểm thử End-to-End cho từng User Story (US) trong dự án **Family Management Backend** (`familyfund-api`).

---

## 📌 Danh Sách User Stories

| Mã US | Tên Tính Năng | Bounded Context | Trạng Thái | File Đặc Tả Kỹ Thuật |
| :---: | :--- | :--- | :---: | :--- |
| **US-01** | **Đăng Ký Tài Khoản Người Khởi Tạo & Tạo Cây Gia Phả Mới** | `com.familyfund.identity` & `genealogy` | ✅ **Đã hoàn thành** | [`US-01-creator-registration-and-clan-creation.md`](./US-01-creator-registration-and-clan-creation.md) |
| **US-03** | **Tạo Hồ Sơ Thành Viên Gia Phả, Chống Trùng & Phân Quyền Theo Chi/Nhánh** | `com.familyfund.genealogy` | ✅ **Đã hoàn thành** | [`US-03-create-member-profile.md`](./US-03-create-member-profile.md) |
| **US-04** | **Quản Lý Chi Phái, Phân Nhánh & Cấu Trúc Phân Cấp Gia Phả** | `com.familyfund.genealogy` | ✅ **Đã hoàn thành** | [`US-04-child-branches-and-hierarchy.md`](./US-04-child-branches-and-hierarchy.md) |
| **US-09** | **Ghi Nhận Đóng Góp Tiền Mặt/Chuyển Khoản & Cấp Phiếu Thu Điện Tử** | `com.familyfund.contribution` | ✅ **Đã hoàn thành (Backend)** | [`US-09-cash-contribution-and-e-receipt.md`](./US-09-cash-contribution-and-e-receipt.md) |
| **US-10** | **Cấu Hình JWT Token, Claims & Middleware Gán Quyền ROLE_CLAN_HEAD** | `com.familyfund.core` & `identity` | ✅ **Đã hoàn thành** | [`US-10-jwt-authentication-and-claims.md`](./US-10-jwt-authentication-and-claims.md) |
| **US-16** | **Tạo Mã Mời (Invite Code) Chung Của Gia Phả** | `com.familyfund.identity` | ✅ **Đã hoàn thành** | [`US-16-general-clan-invite.md`](./US-16-general-clan-invite.md) |
| **US-17** | **Đăng Ký Gia Nhập Dòng Họ Qua Mã Mời** | `com.familyfund.identity` | 📝 *Kế hoạch* | *Chưa khởi tạo* |
| **US-18** | **Đăng Ký Vị Trí & Liên Kết Thành Viên Gia Phả** | `com.familyfund.genealogy` | 📝 *Kế hoạch* | *Chưa khởi tạo* |
| **US-21** | **Khởi Tạo Sự Kiện & Phân Quyền Động (Contextual RBAC)** | `com.familyfund.event` | ✅ **Đã hoàn thành** | [`US-21-create-event-and-contextual-rbac.md`](./US-21-create-event-and-contextual-rbac.md) |
| **US-22** | **Thông Báo Khi Mở Sự Kiện & Cập Nhật Lịch Trình** | `com.familyfund.event` | ✅ **Đã hoàn thành** | [`US-22-event-notification-and-reschedule.md`](./US-22-event-notification-and-reschedule.md) |
| **US-25** | **Cấp Phát Tạm Ứng & Quyết Toán Hóa Đơn Sự Kiện** | `com.familyfund.finance` | 📝 *Kế hoạch* | *Chưa khởi tạo* |
| **US-26** | **Ghi Nhận Giao Dịch Vào Sổ Cái Bất Biến (Immutable Ledger)** | `com.familyfund.finance` | 📝 *Kế hoạch* | *Chưa khởi tạo* |
| **US-30** | **Tiếp Nhận Đóng Góp Quỹ Đa Hình (Tiền/Hiện Vật)** | `com.familyfund.contribution`| 📝 *Kế hoạch* | *Chưa khởi tạo* |

---

## 🛠 Hướng Dẫn Dành Cho Team Khi Bổ Sung User Story Mới

Khi bạn hoàn thành việc phát triển một User Story mới hoặc cần viết đặc tả trước khi code, hãy làm theo 3 bước sau:

1. **Nhân bản file mẫu**:
   Sao chép file [`TEMPLATE-US.md`](./TEMPLATE-US.md) trong thư mục này thành tên mới theo quy tắc:
   ```text
   US-<Mã-Số>-<slug-tính-năng>.md
   ```
   *Ví dụ: `US-17-branch-join-request.md`, `US-25-event-advance-settlement.md`*

2. **Điền đầy đủ các mục trong template**:
   - Tiêu chí chấp nhận (Acceptance Criteria - AC).
   - Bounded Context sở hữu & các class liên quan.
   - Sơ đồ tuần tự tương tác (Mermaid sequence diagram).
   - Lệnh chạy test tự động (`.\gradlew.bat test --tests ...`).
   - Kịch bản cURL mẫu để test trên Swagger / Docker local.

3. **Cập nhật danh mục**:
   - Thêm dòng thông tin của US mới vào bảng danh sách phía trên của file này.
   - Cập nhật liên kết trong [`docs/project-status.md`](../project-status.md).
