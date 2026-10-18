# Hướng Dẫn Phát Triển Dự Án (Developer Onboarding & Guide)

Tài liệu này cung cấp hướng dẫn từng bước để thiết lập môi trường phát triển cục bộ, biên dịch mã nguồn, khởi chạy ứng dụng và chạy kiểm thử cho các thành viên mới tham gia dự án **Family Management Backend** (`familyfund-api`).

---

## 1. Yêu Cầu Môi Trường (Prerequisites)

| Công cụ / Nền tảng | Phiên bản yêu cầu | Ghi chú quan trọng |
| :--- | :--- | :--- |
| **Java Development Kit (JDK)** | **Java 21 (LTS)** | Dự án đã khóa cứng toolchain tại JDK 21. Không cần cài JDK 25. |
| **Docker & Docker Compose** | Docker Desktop bản mới nhất | Cần bật Docker Desktop trước khi chạy các lệnh `docker compose`. |
| **Git** | 2.x+ | Quản lý mã nguồn. |
| **Build Tool** | Gradle Wrapper đi kèm (`gradlew`) | Không cần cài đặt Gradle toàn cục trên máy tính. |

---

## 2. Thiết Lập Môi Trường Cục Bộ (Step-by-Step Setup)

### Bước 1: Khởi động các dịch vụ phụ trợ qua Docker
Trước khi khởi chạy backend, cần khởi động PostgreSQL, Adminer và Swagger UI:

```powershell
# Khởi chạy 3 container ở chế độ nền
docker compose up -d

# Kiểm tra trạng thái cả 3 container đang hoạt động (Status: Up / Healthy)
docker compose ps
```

Các cổng dịch vụ đã được cấu hình:
* **PostgreSQL:** `localhost:5432` (DB: `familyfund_db`, User: `familyfund_user`, Pass: `familyfund_secret`)
* **Adminer Web GUI:** `http://localhost:8088`
* **Swagger UI:** `http://localhost:8082`

### Bước 2: Kiểm tra Java Toolchain
Xác nhận Gradle Wrapper nhận diện đúng JDK 21 trên máy:

```powershell
.\gradlew.bat javaToolchains
```

### Bước 3: Biên dịch mã nguồn
```powershell
.\gradlew.bat compileJava compileTestJava
```

---

## 3. Chạy Kiểm Thử (Testing)

Dự án có bộ kiểm thử tự động toàn diện (bao gồm kiểm thử kiến trúc Spring Modulith, Unit tests, MockMvc controller tests):

```powershell
# Chạy toàn bộ test suite
.\gradlew.bat test

# Chạy kiểm thử kiến trúc module
.\gradlew.bat test --tests "com.familyfund.ArchitectureTest"

# Chạy kiểm thử riêng cho module Identity
.\gradlew.bat test --tests "com.familyfund.identity.*"
```

> 📊 **Báo cáo kết quả kiểm thử:** Sau khi chạy xong, mở file `build/reports/tests/test/index.html` trên trình duyệt để xem chi tiết.

---

## 4. Khởi Chạy Ứng Dụng (Run Application)

```powershell
# Khởi chạy backend với profile dev mặc định (cổng 8080)
.\gradlew.bat bootRun
```

Sau khi ứng dụng khởi chạy thành công:
* API Base URL: `http://localhost:8080`
* Flyway migrations sẽ tự động kích hoạt và cập nhật schema lên PostgreSQL.

---

## 5. Đóng Gói (Package / Build JAR)

```powershell
# Đóng gói file Boot JAR thực thi
.\gradlew.bat clean bootJar
```
File JAR tạo ra tại: `build/libs/familyfund-api-0.0.1-SNAPSHOT.jar`.

