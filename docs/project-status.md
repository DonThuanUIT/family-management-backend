# Trạng Thái Triển Khai Dự Án (Project Status & Roadmap)

Tài liệu này theo dõi tiến độ kỹ thuật, các hạng mục đã hoàn thành, kết quả xác minh thực tế và kế hoạch thực hiện các bước tiếp theo của dự án **Family Management Backend** (`familyfund-api`).

---

## 1. Bảng Trạng Thái Triển Khai Nhiệm Vụ (Task Progress)

| Mã Task | Tên Nhiệm Vụ & Phạm Vi Kỹ Thuật | Trạng Thái | Minh Chứng & Kết Quả Xác Minh |
| :--- | :--- | :--- | :--- |
| **BE-Task 1** | **Thiết kế Database Schema (PostgreSQL) & Môi trường Docker**<br/>- Thiết kế 21 bảng thuộc 4 phân hệ cốt lõi<br/>- Khởi tạo script DDL [`docker/postgres/init.sql`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker/postgres/init.sql)<br/>- Khởi tạo [`docker-compose.yml`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker-compose.yml) (Postgres, Adminer, Swagger)<br/>- Cung cấp tài liệu [`docker/swagger/openapi.yaml`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker/swagger/openapi.yaml)<br/>- Cập nhật [`application.yaml`](file:///d:/WorkSpace/Family-Management/family-management-backend/src/main/resources/application.yaml) & [`.env`](file:///d:/WorkSpace/Family-Management/family-management-backend/.env) | **Đã Hoàn Thành (Implemented)** | - `docker compose config` kiểm tra thành công 100%.<br/>- `.\gradlew.bat clean build -x test` thành công, tạo file JAR `familyfund-api-0.0.1-SNAPSHOT.jar`.<br/>- DDL script bao phủ toàn bộ 21 bảng, giải quyết 2 vòng phụ thuộc và hỗ trợ `pgvector`. |
| **US-04** | **Quản Lý Chi Phái, Phân Nhánh & Cấu Trúc Phân Cấp Gia Phả**<br/>- **Task 1:** Entity `FamilyBranch`, quan hệ phân cấp Adjacency List, DTOs, Mapper<br/>- **Task 2:** Service layer: Root branch, Child branch, Reparenting cascade thế hệ, Chống chu trình `kiemTraChuTrinh`<br/>- **Task 3:** Dựng cây in-memory tối ưu N+1 (`getBranchTree`), Multi-tenant scoping `clanId`<br/>- **Task 4:** Chuẩn hóa dữ liệu 3NF (Flyway `V4__drop_clan_name_from_family_branches.sql`), loại bỏ `CascadeType.REMOVE` an toàn dữ liệu | **Đã Hoàn Thành (Implemented & Verified)** | - 100% automated tests passed (31 unit tests trong module genealogy).<br/>- Triệt tiêu N+1 database queries khi dựng cây phả hệ.<br/>- Tài liệu đặc tả kỹ thuật: [`docs/user-stories/US-04-child-branches-and-hierarchy.md`](./user-stories/US-04-child-branches-and-hierarchy.md). |
| **US-16** | **Tạo Mã Mời (Invite Code) Chung Của Gia Phả**<br/>- **Task 1:** Entity `InvitationCode`, Enums, DTOs, Repository, Generator CSPRNG Base32 (`9e7de92`)<br/>- **Task 2:** Service layer, Business Invariants, Collision retry, Unit tests (`8a6705e`)<br/>- **Task 3:** REST Controller (`/api/v1/invitations`), Method Security, Exception handling, MockMvc tests, OpenAPI spec (`8fb026a`) | **Đã Hoàn Thành (Implemented & Verified)** | - 100% automated tests passed.<br/>- Đã tái cấu trúc vào bounded context `com.familyfund.identity`.<br/>- Tài liệu đặc tả & hướng dẫn kiểm thử E2E: [`docs/user-stories/US-16-general-clan-invite.md`](file:///d:/WorkSpace/Family-Management/family-management-backend/docs/user-stories/US-16-general-clan-invite.md). |
| **ARCH-01** | **Tái Cấu Trúc Kiến Trúc Modular Monolith & Spring Modulith**<br/>- Khóa cứng Java toolchain lên Java 21 LTS.<br/>- Tích hợp Spring Modulith 2.1.1 (`spring-modulith-bom:2.1.1`, core, test).<br/>- Quy hoạch lại package theo Domain Bounded Contexts: `identity`, `genealogy`, `event`, `finance`, `contribution`, `ai`, `core`.<br/>- Tách `api` (Public Contract) và `internal` (Private Implementation).<br/>- Sát nhập tính năng lời mời vào bounded context `identity`.<br/>- Triệt tiêu rò rỉ repository giữa các module: Trích xuất `GenealogyQueryService` thay vì gọi trực tiếp `FamilyBranchRepository`.<br/>- Viết kiểm thử kiến trúc tự động bằng Spring Modulith (`ArchitectureTest`). | **Đã Hoàn Thành (Implemented & Verified)** | - `ArchitectureTest.verifyModularity()` vượt qua thành công 100%.<br/>- Toàn bộ 27/27 tests trong test suite đều pass.<br/>- Tài liệu kiến trúc chuẩn hóa: [`docs/architecture/01-system-architecture.md`](file:///d:/WorkSpace/Family-Management/family-management-backend/docs/architecture/01-system-architecture.md). |
| **BE-Task 2** | **Xây dựng JPA Entities, Enums & Base Repositories cho các phân hệ còn lại**<br/>- Ánh xạ các bảng còn lại sang JPA Entity (Cây gia phả, Sự kiện, Tài chính, Đóng góp) trong package `internal` của từng module<br/>- Cấu hình quan hệ `@ManyToOne`, `@OneToMany`, `@OneToOne`<br/>- Tạo các `JpaRepository` nội bộ trong từng module | *Kế Hoạch (Planned / Next Task)* | Bounded context `identity` và `genealogy` đã có khung chuẩn mực. |
| **BE-Task 3** | **Cấu hình Spring Security, JWT Authentication & Global RBAC**<br/>- Cấu hình xác thực người dùng dựa trên `user_accounts` trong `identity`<br/>- Cấp phát và xác thực JWT token<br/>- Phân quyền theo vai trò hệ thống (`ROLE_ADMIN`, `ROLE_CLAN_HEAD`,...) | *Kế Hoạch (Planned)* | Đã tích hợp sẵn `@EnableMethodSecurity` và `SecurityConfig` trong `core`. |
| **BE-Task 4** | **Triển khai DTOs, Mappers, Services & REST APIs**<br/>- Các API Cây gia phả, Quản lý thành viên (US-17 Tiếp nhận thành viên)<br/>- Các API Sự kiện & Đăng ký hộ gia đình<br/>- Các API Tài chính: Tạm ứng, Quyết toán, Sổ cái bất biến<br/>- Các API Đóng góp & Quản lý tài sản dòng họ | *Kế Hoạch (Planned)* | Phụ thuộc vào BE-Task 3. |

---

## 2. Các Hạng Mục Kỹ Thuật Đã Kiểm Chứng (Verified)

1. **Kiểm Soát Kiến Trúc Bằng Spring Modulith**:
   - Tự động kiểm tra tính đóng gói và ranh giới phụ thuộc liên module qua [`ArchitectureTest.java`](file:///d:/WorkSpace/Family-Management/family-management-backend/src/test/java/com/familyfund/ArchitectureTest.java).
   - Module `identity` chỉ phụ thuộc vào `core` và `genealogy::api`.
   - Ngăn chặn triệt để tình trạng các service truy cập trực tiếp vào repository hoặc entity nội bộ của module khác.
2. **Gradle Build Toolchain & Test Suite**:
   - Khóa cứng Java 21 LTS qua toolchain (`build.gradle.kts`).
   - Biên dịch hoàn tất (`.\gradlew.bat compileJava`, `.\gradlew.bat compileTestJava`).
   - Chạy thành công toàn bộ kiểm thử tự động với tỷ lệ pass 100% (`.\gradlew.bat test`).
3. **Cấu hình Docker Compose**:
   - Đã xác thực cấu hình hợp lệ qua `docker compose config`.
   - Các cổng mạng không bị xung đột: Postgres (`5432`), Adminer (`8088`), Swagger (`8082`), Spring Boot (`8080`).
4. **Mô hình DDL Cơ sở dữ liệu & Flyway Migrations**:
   - Chuẩn hóa CSDL qua Flyway Migrations V1 $\rightarrow$ V4.
   - Xử lý mượt mà 2 trường hợp phụ thuộc vòng (`clans` $\leftrightarrow$ `user_accounts`, `family_branches` $\leftrightarrow$ `members`).
   - Migration V4 hoàn tất chuẩn hóa 3NF: Loại bỏ `clan_name` khỏi `family_branches`, đưa `clans` thành Single Source of Truth.
5. **Bảo mật & Biến môi trường**:
   - Đã bổ sung `.env` và thư mục `bin/` vào file [`.gitignore`](file:///d:/WorkSpace/Family-Management/family-management-backend/.gitignore).
6. **Bảo Toàn Dữ Liệu Phả Hệ & Chống Chu Trình (US-04)**:
   - Loại bỏ `CascadeType.ALL` trên `FamilyBranch` để bảo vệ hồ sơ thành viên không bị xóa nhầm.
   - Thuật toán `kiemTraChuTrinh` ngăn chặn vòng lặp cha-con và bảo toàn tính toàn vẹn của cây phả hệ.
   - Dựng cây đệ quy in-memory loại bỏ hoàn toàn N+1 queries.

---

## 3. Những Điểm Cần Lưu Ý & Các Bước Tiếp Theo

- **Khởi động Docker Desktop**: Khi chạy trên máy tính Windows cá nhân, hãy đảm bảo ứng dụng **Docker Desktop** đã được bật trước khi gõ lệnh `docker compose up -d`.
- **Quy tắc phát triển tính năng mới**:
  - Đặt DTOs và Interfaces giao tiếp liên module trong package `<module>.api` và khai báo `@NamedInterface("api")`.
  - Đặt Entity, Controller, Repository và Service Implementation trong `<module>.internal`.
  - Luôn chạy `.\gradlew.bat test` để đảm bảo `ArchitectureTest` vượt qua trước khi commit code.
