# Hướng Dẫn Phát Triển Dự Án (Developer Onboarding & Guide)

Tài liệu này cung cấp hướng dẫn từng bước để thiết lập môi trường phát triển cục bộ, biên dịch mã nguồn, khởi chạy ứng dụng và chạy kiểm thử cho các thành viên mới tham gia dự án **Family Management Backend** (`familyfund-api`).

---

## 1. Yêu Cầu Môi Trường (Prerequisites)

| Công cụ / Nền tảng | Phiên bản yêu cầu | Ghi chú quan trọng |
| :--- | :--- | :--- |
| **Java Development Kit (JDK)** | **Java 25** | Dự án đã cấu hình toolchain tại JDK 25 (`build.gradle.kts`). Gradle Toolchain tự động quản lý JDK tương thích. |
| **Docker & Docker Compose** | Docker Desktop bản mới nhất | Dùng để chạy Swagger UI và Adminer cục bộ. |
| **Git** | 2.x+ | Quản lý mã nguồn. |
| **Build Tool** | Gradle Wrapper đi kèm (`gradlew`) | Không cần cài đặt Gradle toàn cục trên máy tính. |
| **Cơ sở dữ liệu** | Neon Serverless PostgreSQL | CSDL đám mây PostgreSQL 16 + pgvector, cấu hình qua file `.env`. |

---

## 2. Thiết Lập Môi Trường Cục Bộ (Step-by-Step Setup)

### Bước 1: Cấu hình biến môi trường CSDL (.env)
Sao chép `.env.example` thành `.env` (nếu chưa có) và cập nhật thông tin kết nối tới Neon Serverless PostgreSQL:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://ep-your-project-id.region.aws.neon.tech/neondb?sslmode=require
DB_USER=neondb_owner
DB_PASSWORD=your_neon_password
ADMINER_PORT=8088
SWAGGER_PORT=8082
```

### Bước 2: Khởi động công cụ phụ trợ qua Docker
Khởi động Swagger UI và Adminer qua Docker Compose:

```powershell
# Khởi chạy 2 container ở chế độ nền
docker compose up -d

# Kiểm tra trạng thái container đang hoạt động (Status: Up)
docker compose ps
```

Các cổng dịch vụ đã được cấu hình:
* **Swagger UI:** `http://localhost:8082` (Giao diện tài liệu API OpenAPI 3.0)
* **Adminer Web GUI:** `http://localhost:8088` (Giao diện web quản trị CSDL)

### Bước 3: Kiểm tra Java Toolchain & Biên dịch
Xác nhận Gradle Wrapper nhận diện đúng JDK 25 trên máy:

```powershell
# Kiểm tra toolchain
.\gradlew.bat javaToolchains

# Biên dịch toàn bộ mã nguồn
.\gradlew.bat compileJava compileTestJava
```

---

## 3. Chạy Kiểm Thử (Testing)

Dự án có bộ kiểm thử tự động toàn diện với **275 tests** bảo đảm chất lượng (Kiểm thử kiến trúc Spring Modulith, Unit tests, MockMvc controller tests, E2E Integration tests, Flyway migrations):

```powershell
# Chạy toàn bộ test suite (275 tests)
.\gradlew.bat test

# Chạy kiểm thử kiến trúc module (Spring Modulith verify)
.\gradlew.bat test --tests "com.familyfund.ArchitectureTest"

# Chạy kiểm thử E2E quy trình nộp đơn & gia nhập dòng họ (US-17)
.\gradlew.bat test --tests "com.familyfund.BranchJoinRequestE2EIntegrationTest"

# Chạy kiểm thử riêng cho module Identity (Auth, Invitations, Join Requests)
.\gradlew.bat test --tests "com.familyfund.identity.*"

# Chạy kiểm thử riêng cho module Genealogy (Branches, Members, Hierarchy, Tree)
.\gradlew.bat test --tests "com.familyfund.genealogy.*"

# Chạy kiểm thử riêng cho module Event (Lifecycle, Contextual RBAC, Notifications)
.\gradlew.bat test --tests "com.familyfund.event.*"

# Chạy kiểm thử bộ lọc đa người dùng Core (TenantFilterTest, JwtAuthenticationFilterTest)
.\gradlew.bat test --tests "com.familyfund.core.*"
```

> 📊 **Báo cáo kết quả kiểm thử:** Sau khi chạy xong, mở file `build/reports/tests/test/index.html` trên trình duyệt để xem chi tiết báo cáo HTML.

---

## 4. Khởi Chạy Ứng Dụng (Run Application)

Gradle đã được cấu hình tự động nạp các biến môi trường từ `.env`:

```powershell
# Khởi chạy backend (cổng 8080)
.\gradlew.bat bootRun
```

Sau khi ứng dụng khởi chạy thành công:
* API Base URL: `http://localhost:8080`
* Flyway migrations sẽ tự động kích hoạt và cập nhật schema lên Neon PostgreSQL.
* Mở Swagger UI tại `http://localhost:8082` để thử nghiệm trực tiếp các API.

---

## 5. Đóng Gói (Package / Build JAR)

```powershell
# Đóng gói file Boot JAR thực thi
.\gradlew.bat clean bootJar
```
File JAR tạo ra tại: `build/libs/familyfund-api-0.0.1-SNAPSHOT.jar`.
