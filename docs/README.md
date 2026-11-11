# Tài Liệu Kỹ Thuật Hệ Thống Family Management Backend (`familyfund-api`)

Chào mừng bạn đến với kho tài liệu kỹ thuật chính thức của dự án **Family Management Backend** (`familyfund-api`). 

Dự án được xây dựng theo kiến trúc **Modular Monolith** trên nền tảng **Java 25** (Gradle Toolchain) và **Spring Boot 4.1.1**, kiểm soát ranh giới phụ thuộc nghiêm ngặt bằng **Spring Modulith 2.1.1**, lưu trữ dữ liệu trên **Neon Serverless PostgreSQL (pgvector)** kết hợp Flyway Migrations (V1 $\rightarrow$ V7), và hỗ trợ công cụ Swagger UI / Adminer qua **Docker Compose**.

---

## 📚 Bản Đồ Tài Liệu & Thứ Tự Đọc Khuyến Nghị

Để hiểu rõ hệ thống từ kiến trúc tổng thể, cơ sở dữ liệu, quy chuẩn code đến chi tiết từng tính năng nghiệp vụ, vui lòng theo dõi tài liệu theo cấu trúc dưới đây:

### 1. 🏛 Kiến Trúc & Thiết Kế Nền Tảng ([`docs/architecture/`](./architecture/))
* **[`01-system-architecture.md`](./architecture/01-system-architecture.md)**: Tổng quan kiến trúc Modular Monolith, Bounded Contexts, Tech stack (Java 25, Spring Boot 4.1.1, Spring Modulith 2.1.1, Spring AI 2.0.1), cơ chế Multi-tenancy (`TenantFilter` & `TenantContext`), hạ tầng Docker Compose & Neon Serverless PostgreSQL, giải pháp xử lý phụ thuộc vòng.
* **[`02-database-schema.md`](./architecture/02-database-schema.md)**: Đặc tả chi tiết 23 bảng CSDL PostgreSQL chuẩn hóa 3NF, quan hệ khóa ngoại, chiến lược Indexing, và kịch bản Flyway Migrations (V1 $\rightarrow$ V7).
* **[`03-core-business-flows.md`](./architecture/03-core-business-flows.md)**: Các luồng nghiệp vụ cốt lõi xuyên module (Đăng ký người khởi tạo US-01, Hồ sơ thành viên US-03, Quản lý chi phái US-04, Xác thực JWT US-10, Tạo mã mời chung US-16, Luồng gia nhập Onboarding US-17, Vòng đời sự kiện & Contextual RBAC US-21, Thông báo sự kiện US-22, Tạm ứng $\rightarrow$ Quyết toán, Sổ cái bất biến NFR-01).

### 2. 📋 Danh Mục Đặc Tả User Stories ([`docs/user-stories/`](./user-stories/))
* **[`README.md`](./user-stories/README.md)**: Bảng theo dõi toàn bộ danh sách User Stories (đã hoàn thành & kế hoạch) và hướng dẫn bổ sung US mới.
* **[`TEMPLATE-US.md`](./user-stories/TEMPLATE-US.md)**: **Mẫu chuẩn (Barem)** để các thành viên trong team copy và điền thông tin khi hoàn thành bất kỳ User Story nào.
* **[`US-01-creator-registration-and-clan-creation.md`](./user-stories/US-01-creator-registration-and-clan-creation.md)**: Đăng ký người khởi tạo, tạo gia phả mới nguyên tử, gán vai trò `ROLE_CLAN_HEAD`.
* **[`US-03-create-member-profile.md`](./user-stories/US-03-create-member-profile.md)**: Tạo hồ sơ thành viên gia phả, chống trùng, phân quyền theo chi nhánh và khóa hàng chống race condition.
* **[`US-04-child-branches-and-hierarchy.md`](./user-stories/US-04-child-branches-and-hierarchy.md)**: Đặc tả kỹ thuật đầy đủ của tính năng Quản lý Chi Phái, Phân Nhánh & Cấu Trúc Phân Cấp Gia Phả (Root Branch, Child Branch, Reparenting, In-memory tree assembly N+1, Cycle Prevention).
* **[`US-06-event-attendance-registration.md`](./user-stories/US-06-event-attendance-registration.md)**: Đăng ký tham dự sự kiện & thống kê cỗ bàn (đăng ký hộ gia đình, quy trình 2 bước đề xuất sửa/hủy và phê duyệt của Ban tổ chức).
* **[`US-07-task-and-advance-planning.md`](./user-stories/US-07-task-and-advance-planning.md)**: Khởi tạo hạng mục công việc & phân công nhiệm vụ sự kiện (cá nhân / nhánh gia đình, thông báo bất đồng bộ qua Domain Events).
* **[`US-08-task-deadline-progress-warning.md`](./user-stories/US-08-task-deadline-progress-warning.md)**: Background worker quét định kỳ công việc chưa hoàn thành, tạo vết cảnh báo tiến độ `ProgressWarning` (`task_progress_alerts`, `Reminder24H`) và gửi thông báo tới `assigned_member_id` khi hạn chót còn ≤ 24 giờ.
* **[`US-09-cash-contribution-and-e-receipt.md`](./user-stories/US-09-cash-contribution-and-e-receipt.md)**: Ghi nhận đóng góp tiền mặt/chuyển khoản, phân bổ công đức & cấp phiếu thu điện tử (`PT-YYYYMMDD-XXXXX`).
* **[`US-10-in-kind-contribution-and-clan-assets.md`](./user-stories/US-10-in-kind-contribution-and-clan-assets.md)**: Ghi nhận hiện vật cúng tiến, quy đổi giá trị theo định mức/hóa đơn/thỏa thuận & quản lý vòng đời tài sản dòng họ.
* **[`US-10-jwt-authentication-and-claims.md`](./user-stories/US-10-jwt-authentication-and-claims.md)**: Cấu hình JWT Token, Claims, trích xuất quyền và Middleware gán `ROLE_CLAN_HEAD`.
* **[`US-16-general-clan-invite.md`](./user-stories/US-16-general-clan-invite.md)**: Đặc tả kỹ thuật đầy đủ của tính năng tạo mã mời chung dòng họ, kiểm soát bất biến, CSPRNG Base32, sequence diagram và kiểm thử E2E.
* **[`US-17-branch-join-request.md`](./user-stories/US-17-branch-join-request.md)**: Đăng ký gia nhập dòng họ qua mã mời chung (Onboarding, cây thu gọn, nộp đơn đề xuất vị trí, quy trình phê duyệt cross-module).
* **[`US-18-create-virtual-member-and-generate-position-link-code.md`](./user-stories/US-18-create-virtual-member-and-generate-position-link-code.md)**: Đặc tả kỹ thuật đầy đủ của tính năng tạo hồ sơ ảo (vị trí lá, chưa có tài khoản) và phát hành mã `PositionLinkCode` dùng một lần, gồm ranh giới `identity → genealogy::api`, bảng ánh xạ schema, sequence diagram, ma trận HTTP `201/400/401/403/404/409` và kịch bản kiểm thử E2E.
* **[`US-19-enter-position-link-code-and-join-automatically.md`](./user-stories/US-19-enter-position-link-code-and-join-automatically.md)**: Đặc tả kỹ thuật đầy đủ của tính năng nhập `PositionLinkCode` khi đăng ký để tự động liên kết tài khoản (mới hoặc đã có) vào đúng vị trí/chi/nhánh và kích hoạt `Active` không cần phê duyệt, gồm sequence diagram, bảng ánh xạ schema, ma trận HTTP `200/400/403/404/409` và kịch bản kiểm thử E2E.
* **[`US-21-create-event-and-contextual-rbac.md`](./user-stories/US-21-create-event-and-contextual-rbac.md)**: Khởi tạo sự kiện, máy trạng thái vòng đời sự kiện và phân quyền theo ngữ cảnh (`NhanSuSuKien`).
* **[`US-22-member-profile-and-password-management.md`](./user-stories/US-22-member-profile-and-password-management.md)**: Thành viên tự xem và cập nhật thông tin liên lạc cá nhân (SĐT, email, địa chỉ), bảo vệ thông tin phả hệ cố định và tự đổi mật khẩu.
* **[`US-25-member-personal-data-privacy.md`](./user-stories/US-25-member-personal-data-privacy.md)**: Bảo vệ thông tin cá nhân thành viên theo mô hình Hybrid Privacy (Privacy by Default, Owner Consent và RBAC phạm vi Trưởng tộc/Trưởng nhánh).

### 3. 🛠 Quy Chuẩn & Hướng Dẫn Phát Triển ([`docs/guidelines/`](./guidelines/))
* **[`development-guide.md`](./guidelines/development-guide.md)**: Hướng dẫn thiết lập môi trường cho dev mới (JDK 25, cấu hình `.env` cho Neon Cloud, Docker Compose cho Swagger/Adminer, lệnh biên dịch Gradle, chạy test suite 275 tests, chạy backend).
* **[`modulith-rules.md`](./guidelines/modulith-rules.md)**: **Quy tắc vàng khi code Modular Monolith**: quy tắc phân chia `api` (public contract) vs `internal` (private implementation), cấm rò rỉ Repository, và chạy kiểm thử kiến trúc tự động (`ArchitectureTest`).

### 4. 📊 Bảng Theo Dõi Dự Án
* **[`project-status.md`](./project-status.md)**: Dashboard theo dõi tiến độ từng nhiệm vụ (Tasks progress), danh sách automated tests đã kiểm chứng thực tế (kèm số test từng class) và roadmap tiếp theo.
