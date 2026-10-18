# Danh Mục Đặc Tả Kỹ Thuật User Stories (`docs/user-stories/`)

Thư mục này lưu trữ toàn bộ các tài liệu đặc tả kỹ thuật chi tiết, thiết kế kiến trúc, mô hình dữ liệu, sequence diagram và hướng dẫn kiểm thử End-to-End cho từng User Story (US) trong dự án **Family Management Backend** (`familyfund-api`).

---

## 📌 Danh Sách User Stories

| Mã US | Tên Tính Năng | Bounded Context | Trạng Thái | File Đặc Tả Kỹ Thuật |
| :---: | :--- | :--- | :---: | :--- |
| **US-16** | **Tạo Mã Mời (Invite Code) Chung Của Gia Phả** | `com.familyfund.identity` | ✅ **Đã hoàn thành** | [`US-16-general-clan-invite.md`](./US-16-general-clan-invite.md) |
| **US-17** | **Đăng Ký Gia Nhập Dòng Họ Qua Mã Mời** | `com.familyfund.identity` | 📝 *Kế hoạch* | *Chưa khởi tạo* |
| **US-18** | **Quản Lý Chi Phái & Phân Nhánh Gia Phả** | `com.familyfund.genealogy` | 📝 *Kế hoạch* | *Chưa khởi tạo* |
| **US-21** | **Khởi Tạo Sự Kiện & Phân Quyền Động (Contextual RBAC)** | `com.familyfund.event` | 📝 *Kế hoạch* | *Chưa khởi tạo* |
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

