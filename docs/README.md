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
* **[`US-04-child-branches-and-hierarchy.md`](./user-stories/US-04-child-branches-and-hierarchy.md)**: Quản lý chi phái, phân nhánh & cấu trúc phân cấp gia phả (Root Branch, Child Branch, Reparenting, dựng cây in-memory không N+1, Cycle Prevention).
* **[`US-10-jwt-authentication-and-claims.md`](./user-stories/US-10-jwt-authentication-and-claims.md)**: Cấu hình JWT Token, Claims, trích xuất quyền và Middleware gán `ROLE_CLAN_HEAD`.
* **[`US-16-general-clan-invite.md`](./user-stories/US-16-general-clan-invite.md)**: Tạo mã mời chung dòng họ, kiểm soát bất biến, CSPRNG Base32, sequence diagram và kiểm thử E2E.
* **[`US-17-branch-join-request.md`](./user-stories/US-17-branch-join-request.md)**: Đăng ký gia nhập dòng họ qua mã mời chung (Onboarding, cây thu gọn, nộp đơn đề xuất vị trí, quy trình phê duyệt cross-module).
* **[`US-21-create-event-and-contextual-rbac.md`](./user-stories/US-21-create-event-and-contextual-rbac.md)**: Khởi tạo sự kiện, máy trạng thái vòng đời sự kiện và phân quyền theo ngữ cảnh (`NhanSuSuKien`).
* **[`US-22-event-notification-and-reschedule.md`](./user-stories/US-22-event-notification-and-reschedule.md)**: Thông báo sự kiện bất đồng bộ, lắng nghe Domain Events và tự động phát hiện sai khác lịch trình (schedule diff).

### 3. 🛠 Quy Chuẩn & Hướng Dẫn Phát Triển ([`docs/guidelines/`](./guidelines/))
* **[`development-guide.md`](./guidelines/development-guide.md)**: Hướng dẫn thiết lập môi trường cho dev mới (JDK 25, cấu hình `.env` cho Neon Cloud, Docker Compose cho Swagger/Adminer, lệnh biên dịch Gradle, chạy test suite 275 tests, chạy backend).
* **[`modulith-rules.md`](./guidelines/modulith-rules.md)**: **Quy tắc vàng khi code Modular Monolith**: quy tắc phân chia `api` (public contract) vs `internal` (private implementation), cấm rò rỉ Repository, và chạy kiểm thử kiến trúc tự động (`ArchitectureTest`).

### 4. 📊 Bảng Theo Dõi Dự Án
* **[`project-status.md`](./project-status.md)**: Dashboard theo dõi tiến độ từng nhiệm vụ (Tasks progress), 275 automated tests đã kiểm chứng thực tế và roadmap tiếp theo.
