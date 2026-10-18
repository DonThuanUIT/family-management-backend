# Family Management Backend (`familyfund-api`)

Dự án backend quản lý quỹ gia đình và tài chính xây dựng trên nền tảng **Spring Boot**, tích hợp **Spring AI** cùng cơ sở dữ liệu **PostgreSQL (PGVector)**. Hệ thống quản lý build đã được chuyển đổi toàn diện sang **Gradle Wrapper (Kotlin DSL)** với cơ chế **Java Toolchain** khóa cứng Java 21 LTS.

---

## 🛠 Tech Stack & Requirements

- **Java Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 4.1.1
- **Architecture**: Modular Monolith with Spring Modulith 2.1.1
- **AI Integration**: Spring AI 2.0.1 (Vector Store PGVector)
- **Database**: PostgreSQL with pgvector extension (Flyway Migrations)
- **Build Tool**: Gradle Wrapper 9.x+ (Kotlin DSL: `build.gradle.kts`, `settings.gradle.kts`)

> **Lưu ý về Java Toolchain**: Dự án đã cấu hình `java.toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }`. Ngay cả khi máy tính của bạn cài đặt song song nhiều phiên bản JDK (ví dụ: Java 17, 21, 25), Gradle sẽ luôn tự động nhận diện và ép buộc sử dụng đúng **JDK 21** khi biên dịch và chạy kiểm thử mà không phụ thuộc vào biến môi trường `JAVA_HOME`.

---

## 🚀 Hướng Dẫn Sử Dụng Gradle Wrapper

Bạn có thể chạy trực tiếp bộ Gradle Wrapper đi kèm mà không cần cài đặt Gradle trên máy tính:

- **Windows (PowerShell / CMD)**: `.\gradlew.bat <task>`
- **Linux / macOS**: `./gradlew <task>`

*(Trong các lệnh mẫu bên dưới, nếu bạn dùng Linux/macOS, hãy thay `.\gradlew.bat` bằng `./gradlew`)*

### 1. Kiểm tra Môi trường & Xác thực Java Toolchain
Kiểm tra phiên bản Gradle và danh sách các JDK được Gradle tự động phát hiện trên hệ thống:

```bash
# Kiểm tra phiên bản Gradle và JVM khởi chạy
.\gradlew.bat -v

# Kiểm tra danh sách Java Toolchains (Xác nhận JDK 21 được nhận diện)
.\gradlew.bat javaToolchains
```

---

### 2. Biên dịch Mã Nguồn (Compile)
Biên dịch toàn bộ code Java trong `src/main`:

```bash
.\gradlew.bat compileJava
```

Biên dịch cả mã nguồn kiểm thử trong `src/test`:

```bash
.\gradlew.bat compileTestJava
```

---

### 3. Chạy Kiểm Thử (Test)
Chạy bộ kiểm thử JUnit Platform và tạo báo cáo kết quả:

```bash
# Chạy toàn bộ test
.\gradlew.bat test

# Chạy một test class cụ thể
.\gradlew.bat test --tests "com.familyfund.FamilyfundApiApplicationTests"
```

> Báo cáo kết quả kiểm thử dạng HTML sẽ được tự động tạo tại:  
> `build/reports/tests/test/index.html`

---

### 4. Đóng Gói Ứng Dụng (Package / Build)

```bash
# Đóng gói file Boot JAR thực thi
.\gradlew.bat bootJar

# Làm sạch thư mục build cũ và build trọn gói (bỏ qua kiểm thử khi chưa kết nối Database)
.\gradlew.bat clean build -x test

# Build đầy đủ bao gồm cả chạy test
.\gradlew.bat clean build
```

> File JAR sau khi đóng gói sẽ nằm tại thư mục:  
> `build/libs/familyfund-api-0.0.1-SNAPSHOT.jar`

---

### 5. Khởi Chạy Ứng Dụng (Run Application)
Chạy trực tiếp ứng dụng Spring Boot trong môi trường phát triển:

```bash
# Chạy với profile mặc định
.\gradlew.bat bootRun

# Chạy với profile chỉ định (ví dụ: dev)
.\gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

Hoặc chạy từ file JAR đã build:

```bash
java -jar build/libs/familyfund-api-0.0.1-SNAPSHOT.jar
```

---

### 6. Kiểm tra Cây Phụ Thuộc (Dependencies)
Xem toàn bộ cây thư viện phụ thuộc và kiểm tra phiên bản:

```bash
# Xem toàn bộ dependencies của runtime classpath
.\gradlew.bat dependencies --configuration runtimeClasspath

# Tìm kiếm thư viện cụ thể trong cây dependencies
.\gradlew.bat dependencyInsight --dependency spring-ai-bom --configuration runtimeClasspath
```


```text
family-management-backend/
├── .env                       # Biến môi trường cho Docker Compose
├── .env.example               # Mẫu cấu hình biến môi trường
├── docker-compose.yml         # Khởi tạo Postgres, Adminer, Swagger UI
├── docker/
│   ├── postgres/
│   │   └── init.sql           # Kịch bản DDL khởi tạo 21 bảng cốt lõi & dữ liệu mẫu
│   └── swagger/
│       └── openapi.yaml       # Tài liệu API OpenAPI 3.0 cho Swagger UI
├── docs/                      # Tài liệu kỹ thuật chi tiết của dự án
│   ├── README.md              # Bản đồ tài liệu & thứ tự đọc
│   ├── project-status.md      # Trạng thái dự án & Kế hoạch tiếp theo
│   ├── architecture/          # Kiến trúc hệ thống, Database Schema & Business Flows
│   │   ├── 01-system-architecture.md
│   │   ├── 02-database-schema.md
│   │   └── 03-core-business-flows.md
│   ├── user-stories/          # Đặc tả kỹ thuật & Hướng dẫn kiểm thử từng User Story
│   │   ├── README.md          # Danh mục User Stories & hướng dẫn bổ sung
│   │   ├── TEMPLATE-US.md     # Mẫu chuẩn (Barem) cho US mới
│   │   └── US-16-general-clan-invite.md
│   └── guidelines/            # Quy chuẩn phát triển & coding conventions
│       ├── development-guide.md
│       └── modulith-rules.md
├── build.gradle.kts           # Kịch bản build & dependencies (Kotlin DSL, Java 21)
├── settings.gradle.kts        # Cấu hình root project
├── gradlew                    # Gradle wrapper script cho Linux/macOS
├── gradlew.bat                # Gradle wrapper script cho Windows
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── src/
    ├── main/
    │   ├── java/com/familyfund/
    │   │   ├── FamilyfundApiApplication.java
    │   │   ├── core/          # Shared Kernel (Security, Global Exceptions, ApiResponse)
    │   │   ├── identity/      # Module Định danh, Tài khoản & Lời mời (api & internal)
    │   │   ├── genealogy/     # Module Dòng họ, Chi phái & Thành viên (api & internal)
    │   │   ├── event/         # Module Quản lý Sự kiện & Contextual RBAC
    │   │   ├── finance/       # Module Tạm ứng, Quyết toán & Sổ cái bất biến
    │   │   ├── contribution/  # Module Đóng góp & Quản lý tài sản
    │   │   └── ai/            # Module Trợ lý AI & Vector Store
    │   └── resources/
    │       ├── application.yaml
    │       └── db/migration/  # Flyway database migrations
    └── test/
        └── java/com/familyfund/
            ├── ArchitectureTest.java # Spring Modulith Architecture Verification
            ├── DatabaseMigrationTest.java
            ├── FamilyfundApiApplicationTests.java
            └── identity/      # Unit & Controller Tests cho Identity & Invitation
```

> 📖 **Xem chi tiết tài liệu hệ thống tại thư mục [docs/](file:///d:/WorkSpace/Family-Management/family-management-backend/docs/README.md)**.


---

## 🐳 Môi Trường Docker (PostgreSQL, Adminer, Swagger)

Dự án cung cấp bộ kịch bản `docker-compose.yml` tích hợp sẵn 3 dịch vụ phụ trợ quan trọng:

1. **PostgreSQL 16 + PGVector (`familyfund-postgres`)**:
   - Port: `5432`
   - Database: `familyfund_db`
   - User: `familyfund_user`
   - Password: `familyfund_secret`
   - Tự động chạy kịch bản `docker/postgres/init.sql` khởi tạo toàn bộ 21 bảng thuộc 4 phân hệ và seed data cơ sở.
2. **Adminer Database Manager (`familyfund-adminer`)**:
   - Port: `8088` (truy cập tại: `http://localhost:8088`)
   - Tự động kết nối tới container `postgres`.
3. **Swagger UI (`familyfund-swagger`)**:
   - Port: `8082` (truy cập tại: `http://localhost:8082`)
   - Tải trực tiếp tài liệu OpenAPI từ `docker/swagger/openapi.yaml`.

### Khởi chạy các container:

```bash
# 1. Khởi động Docker Desktop trên máy
# 2. Khởi chạy 3 container ở chế độ nền:
docker compose up -d

# Kiểm tra trạng thái các container:
docker compose ps

# Xem log khởi tạo database:
docker compose logs postgres

# Dừng các container:
docker compose down
```