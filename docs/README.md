# Tài Liệu Kỹ Thuật Hệ Thống Family Management Backend (`familyfund-api`)

Chào mừng bạn đến với kho tài liệu kỹ thuật chính thức của dự án **Family Management Backend** (`familyfund-api`). 

Dự án được xây dựng theo kiến trúc **Modular Monolith** trên nền tảng **Java 21 LTS** và **Spring Boot 4.1.1**, kiểm soát ranh giới phụ thuộc nghiêm ngặt bằng **Spring Modulith 2.1.1**, lưu trữ dữ liệu trên **Neon Serverless PostgreSQL (pgvector)** kết hợp Flyway Migrations, và hỗ trợ công cụ Swagger UI / Adminer qua **Docker Compose**.

---

## 📚 Bản Đồ Tài Liệu & Thứ Tự Đọc Khuyến Nghị

Để hiểu rõ hệ thống từ kiến trúc tổng thể, cơ sở dữ liệu, quy chuẩn code đến chi tiết từng tính năng nghiệp vụ, vui lòng theo dõi tài liệu theo cấu trúc dưới đây:

### 1. 🏛 Kiến Trúc & Thiết Kế Nền Tảng ([`docs/architecture/`](./architecture/))
* **[`01-system-architecture.md`](./architecture/01-system-architecture.md)**: Tổng quan kiến trúc Modular Monolith, Bounded Contexts, Tech stack (Java 21 LTS, Spring Boot 4.1.1, Spring Modulith 2.1.1, Spring AI), cơ chế Multi-tenancy (`TenantFilter` & `TenantContext`), hạ tầng Docker Compose & Neon Serverless PostgreSQL, giải pháp xử lý phụ thuộc vòng.
* **[`02-database-schema.md`](./architecture/02-database-schema.md)**: Đặc tả chi tiết 23 bảng CSDL PostgreSQL chuẩn hóa 3NF, quan hệ khóa ngoại, chiến lược Indexing, và kịch bản Flyway Migrations (V1 $\rightarrow$ V4).
* **[`03-core-business-flows.md`](./architecture/03-core-business-flows.md)**: 7 luồng nghiệp vụ cốt lõi xuyên module (Định danh gia phả, Contextual RBAC, Tạm ứng $\rightarrow$ Quyết toán, Đóng góp quỹ họ, Sổ cái bất biến NFR-01, Tạo mã mời chung US-16, Quản lý chi phái & phân cấp phả hệ US-04).

### 2. 📋 Danh Mục Đặc Tả User Stories ([`docs/user-stories/`](./user-stories/))
* **[`README.md`](./user-stories/README.md)**: Bảng theo dõi toàn bộ danh sách User Stories (đã hoàn thành & kế hoạch) và hướng dẫn bổ sung US mới.
* **[`TEMPLATE-US.md`](./user-stories/TEMPLATE-US.md)**: **Mẫu chuẩn (Barem)** để các thành viên trong team copy và điền thông tin khi hoàn thành bất kỳ User Story nào.
* **[`US-04-child-branches-and-hierarchy.md`](./user-stories/US-04-child-branches-and-hierarchy.md)**: Đặc tả kỹ thuật đầy đủ của tính năng Quản lý Chi Phái, Phân Nhánh & Cấu Trúc Phân Cấp Gia Phả (Root Branch, Child Branch, Reparenting, In-memory tree assembly N+1, Cycle Prevention).
* **[`US-16-general-clan-invite.md`](./user-stories/US-16-general-clan-invite.md)**: Đặc tả kỹ thuật đầy đủ của tính năng tạo mã mời chung dòng họ, kiểm soát bất biến, CSPRNG Base32, sequence diagram và kiểm thử E2E.

### 3. 🛠 Quy Chuẩn & Hướng Dẫn Phát Triển ([`docs/guidelines/`](./guidelines/))
* **[`development-guide.md`](./guidelines/development-guide.md)**: Hướng dẫn thiết lập môi trường cho dev mới (JDK 21, cấu hình `.env` cho Neon Cloud, Docker Compose cho Swagger/Adminer, lệnh biên dịch Gradle, chạy test suite 70 tests, chạy backend).
* **[`modulith-rules.md`](./guidelines/modulith-rules.md)**: **Quy tắc vàng khi code Modular Monolith**: quy tắc phân chia `api` (public contract) vs `internal` (private implementation), cấm rò rỉ Repository, và chạy kiểm thử kiến trúc tự động (`ArchitectureTest`).

### 4. 📊 Bảng Theo Dõi Dự Án
* **[`project-status.md`](./project-status.md)**: Dashboard theo dõi tiến độ từng nhiệm vụ (Tasks progress), 70 automated tests đã kiểm chứng thực tế và roadmap tiếp theo.
