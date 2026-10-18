# Trạng Thái Triển Khai Dự Án (Project Status & Roadmap)

Tài liệu này theo dõi tiến độ kỹ thuật, các hạng mục đã hoàn thành, kết quả xác minh thực tế và kế hoạch thực hiện các bước tiếp theo của dự án **Family Management Backend** (`familyfund-api`).

---

## 1. Bảng Trạng Thái Triển Khai Nhiệm Vụ (Task Progress)

| Mã Task | Tên Nhiệm Vụ & Phạm Vi Kỹ Thuật | Trạng Thái | Minh Chứng & Kết Quả Xác Minh |
| :--- | :--- | :--- | :--- |
| **BE-Task 1** | **Thiết kế Database Schema (PostgreSQL) & Môi trường Docker**<br/>- Thiết kế 21 bảng thuộc 4 phân hệ cốt lõi<br/>- Khởi tạo script DDL [`docker/postgres/init.sql`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker/postgres/init.sql)<br/>- Khởi tạo [`docker-compose.yml`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker-compose.yml) (Postgres, Adminer, Swagger)<br/>- Cung cấp tài liệu [`docker/swagger/openapi.yaml`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker/swagger/openapi.yaml)<br/>- Cập nhật [`application.yaml`](file:///d:/WorkSpace/Family-Management/family-management-backend/src/main/resources/application.yaml) & [`.env`](file:///d:/WorkSpace/Family-Management/family-management-backend/.env) | **Đã Hoàn Thành (Implemented)** | - `docker compose config` kiểm tra thành công 100%.<br/>- `.\gradlew.bat clean build -x test` thành công, tạo file JAR `familyfund-api-0.0.1-SNAPSHOT.jar`.<br/>- DDL script bao phủ toàn bộ 21 bảng, giải quyết 2 vòng phụ thuộc và hỗ trợ `pgvector`. |
| **US-16** | **Tạo Mã Mời (Invite Code) Chung Của Gia Phả**<br/>- **Task 1:** Entity `InvitationCode`, Enums, DTOs, Repository, Generator CSPRNG Base32 (`9e7de92`)<br/>- **Task 2:** Service layer, Business Invariants, Collision retry, Unit tests (`8a6705e`)<br/>- **Task 3:** REST Controller (`/api/v1/invitations`), Method Security, Exception handling, MockMvc tests, OpenAPI spec (`8fb026a`) | **Đã Hoàn Thành (Implemented & Verified)** | - 10/10 automated tests passed (Unit + MockMvc).<br/>- Build JAR thành công không lỗi.<br/>- Tài liệu đặc tả & hướng dẫn kiểm thử E2E: [`docs/04-us16-general-clan-invite.md`](file:///d:/WorkSpace/Family-Management/family-management-backend/docs/04-us16-general-clan-invite.md). |
| **BE-Task 2** | **Xây dựng JPA Entities, Enums & Base Repositories cho các phân hệ còn lại**<br/>- Ánh xạ các bảng còn lại sang JPA Entity (Cây gia phả, Sự kiện, Tài chính, Đóng góp)<br/>- Cấu hình quan hệ `@ManyToOne`, `@OneToMany`, `@OneToOne`<br/>- Tạo các `JpaRepository` tương ứng cho từng phân hệ | *Kế Hoạch (Planned / Next Task)* | Phân hệ `invitation` đã hoàn thành mẫu chuẩn mực. |
| **BE-Task 3** | **Cấu hình Spring Security, JWT Authentication & Global RBAC**<br/>- Cấu hình xác thực người dùng dựa trên `user_accounts`<br/>- Cấp phát và xác thực JWT token<br/>- Phân quyền theo vai trò hệ thống (`ROLE_ADMIN`, `ROLE_CLAN_HEAD`,...) | *Kế Hoạch (Planned)* | Đã tích hợp sẵn `@EnableMethodSecurity` và `SecurityConfig` cơ bản. |
| **BE-Task 4** | **Triển khai DTOs, Mappers, Services & REST APIs**<br/>- Các API Cây gia phả, Quản lý thành viên (US-17 Tiếp nhận thành viên)<br/>- Các API Sự kiện & Đăng ký hộ gia đình<br/>- Các API Tài chính: Tạm ứng, Quyết toán, Sổ cái bất biến<br/>- Các API Đóng góp & Quản lý tài sản dòng họ | *Kế Hoạch (Planned)* | Phụ thuộc vào BE-Task 3. |

---

## 2. Các Hạng Mục Kỹ Thuật Đã Kiểm Chứng (Verified)

1. **Gradle Build Toolchain**:
   - Khóa cứng Java 21 LTS qua toolchain thành công.
   - Biên dịch hoàn tất (`.\gradlew.bat compileJava`).
   - Đóng gói JAR thành công (`.\gradlew.bat bootJar`).
2. **Cấu hình Docker Compose**:
   - Đã xác thực cấu hình hợp lệ qua `docker compose config`.
   - Các cổng mạng không bị xung đột: Postgres (`5432`), Adminer (`8088`), Swagger (`8082`), Spring Boot (`8080`).
3. **Mô hình DDL Cơ sở dữ liệu**:
   - 21 bảng dữ liệu được chuẩn hóa, có đầy đủ ràng buộc `PRIMARY KEY`, `FOREIGN KEY`, `CHECK constraints` và `UNIQUE constraints`.
   - Thiết lập chỉ mục tối ưu (`INDEX`) cho các trường khóa ngoại, số điện thoại, tình trạng và ngày tháng.
   - Xử lý mượt mà 2 trường hợp phụ thuộc vòng (`clans` $\leftrightarrow$ `user_accounts`, `family_branches` $\leftrightarrow$ `members`).
   - Sẵn sàng kích hoạt extension `vector` cho Spring AI.
4. **Bảo mật & Biến môi trường**:
   - Đã bổ sung `.env` và thư mục `bin/` vào file [`.gitignore`](file:///d:/WorkSpace/Family-Management/family-management-backend/.gitignore).

---

## 3. Những Điểm Cần Lưu Ý & Các Bước Tiếp Theo

- **Khởi động Docker Desktop**: Khi chạy trên máy tính Windows cá nhân, hãy đảm bảo ứng dụng **Docker Desktop** đã được bật trước khi gõ lệnh `docker compose up -d`.
- **Triển khai BE-Task 2**: Bắt đầu ánh xạ các bảng cơ sở dữ liệu đã thiết kế thành các lớp thực thể JPA (`@Entity`) trong thư mục `src/main/java/com/familyfund/modules/`.

