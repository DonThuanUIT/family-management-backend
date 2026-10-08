# Family Management Backend (`familyfund-api`)

Dự án backend quản lý quỹ gia đình và tài chính xây dựng trên nền tảng **Spring Boot**, tích hợp **Spring AI** cùng cơ sở dữ liệu **PostgreSQL (PGVector)**. Hệ thống quản lý build đã được chuyển đổi toàn diện sang **Gradle Wrapper (Kotlin DSL)** với cơ chế **Java Toolchain** khóa cứng Java 21 LTS.

---

## 🛠 Tech Stack & Requirements

- **Java Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 4.1.1
- **AI Integration**: Spring AI 2.0.1 (Vector Store PGVector)
- **Database**: PostgreSQL with pgvector extension
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


## 📁 Cấu Trúc Dự Án

```text
family-management-backend/
├── .gitignore
├── README.md
├── build.gradle.kts           # Kịch bản build & dependencies (Kotlin DSL)
├── settings.gradle.kts        # Cấu hình root project
├── gradlew                    # Gradle wrapper script cho Linux/macOS
├── gradlew.bat                # Gradle wrapper script cho Windows
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── src/
    ├── main/
    │   ├── java/com/familyfund/...
    │   └── resources/
    │       └── application.yaml
    └── test/
        └── java/com/familyfund/...
```