# Kiến Trúc Hệ Thống & Hạ Tầng (System Architecture)

Tài liệu này mô tả kiến trúc tổng thể, mô hình Modular Monolith, ranh giới module với Spring Modulith, công nghệ nền tảng và môi trường hạ tầng của hệ thống **Family Management Backend** (`familyfund-api`).

---

## 1. Bối Cảnh Nghiệp Vụ & Mục Tiêu

Hệ thống **Family Management Backend** giải quyết bài toán chuyển đổi số công tác quản trị dòng họ và quỹ tài chính gia đình, bao gồm:
- **Quản lý phả hệ đa cấp**: Lưu trữ cây phả hệ qua nhiều thế hệ, phân chia chi/nhánh với mô hình đa dòng họ (Multi-tenant).
- **Quản lý sự kiện & Phân quyền động**: Tổ chức các ngày giỗ tổ, lễ hội, mừng thọ với cơ chế phân quyền ngữ cảnh (Contextual RBAC) tự động thu hồi khi sự kiện kết thúc.
- **Minh bạch hóa tài chính**: Quy trình cấp phát tạm ứng, thanh quyết toán hóa đơn thực tế và chốt sổ quỹ sự kiện.
- **Sổ cái Quỹ chung bất biến (Immutable Ledger)**: Lưu vết toàn bộ biến động tài chính dòng họ, ngăn chặn sửa/xóa dữ liệu lịch sử và chỉ cho phép giao dịch bù trừ (Audit Trail NFR-01).
- **Huy động đóng góp đa hình**: Tiếp nhận tiền mặt/chuyển khoản và hiện vật/lễ vật cúng tiến, quy đổi giá trị và chuyển vào danh mục tài sản lâu dài của dòng họ.

---

## 2. Công Nghệ Nền Tảng (Tech Stack)

```mermaid
graph TD
    Client["Clients (Mobile / Web App / Swagger UI)"] -->|RESTful APIs / HTTP JSON| SBApp["Spring Boot 4.1.1 (Java 21 LTS)"]
    SBApp -->|Spring Modulith 2.1.1| Modules["Domain Bounded Contexts (identity, genealogy, ...)"]
    SBApp -->|Spring Data JPA / Hibernate| PG[("PostgreSQL 16 + pgvector")]
    SBApp -->|Spring AI 2.0.1| VectorExt["Vector Store Extension (AI Embedding)"]
    Admin["Admin / Developers"] -->|Web Browser :8088| Adminer["Adminer Container"]
    Adminer -->|SQL Connection| PG
    SwaggerUI["Swagger UI Container :8082"] -->|Load OpenAPI Spec| Spec["docker/swagger/openapi.yaml"]
```

| Thành phần | Công nghệ / Phiên bản | Vai trò & Đặc điểm kỹ thuật |
| :--- | :--- | :--- |
| **Ngôn ngữ & Runtime** | Java 21 (LTS) | Khóa cứng qua Gradle Toolchain (`languageVersion.set(21)`), tận dụng Virtual Threads và tính năng hiện đại của Java. |
| **Framework cốt lõi** | Spring Boot 4.1.1 | Nền tảng Spring Boot thế hệ mới với hiệu năng cao, Spring Data JPA, Spring Security, Validation. |
| **Kiến trúc Module** | Spring Modulith 2.1.1 | Quản lý và kiểm soát ranh giới module (Modular Monolith), đóng gói package nội bộ và kiểm thử kiến trúc tự động (`verify()`). |
| **Công nghệ AI** | Spring AI 2.0.1 (`spring-ai-starter-vector-store-pgvector`) | Hỗ trợ lưu trữ vector embedding gia phả, nghị quyết họ tộc phục vụ trợ lý AI thông minh (module `com.familyfund.ai`). |
| **Cơ sở dữ liệu** | PostgreSQL 16 (`pgvector/pgvector:pg16`) | RDBMS hỗ trợ ACID nghiêm ngặt, JSONB, và mở rộng `vector` cho AI. Quản lý schema bằng Flyway Migration. |
| **Build & Dependency** | Gradle Wrapper 9.x (Kotlin DSL) | Quản lý build nhất quán qua `build.gradle.kts`, độc lập môi trường nhờ wrapper. |
| **Quản trị CSDL Web** | Adminer (Latest) | Giao diện web trực quan để kiểm tra dữ liệu và truy vấn SQL trên container. |
| **Đặc tả & Giao diện API** | Swagger UI (`swaggerapi/swagger-ui:latest`) | Hiển thị và tương tác trực tiếp với API thông qua tài liệu OpenAPI 3.0. |

---

## 3. Kiến Trúc Modular Monolith & Ranh Giới Bounded Contexts

Hệ thống được thiết kế theo mô hình **Modular Monolith**, phân chia ranh giới theo các Bounded Context của Domain-Driven Design (DDD) và được kiểm soát biên giới bằng **Spring Modulith 2.1.1**.

### 3.1. Danh Mục Các Bounded Contexts

```text
src/main/java/com/familyfund/
├── FamilyfundApiApplication.java    # Điểm khởi chạy ứng dụng Spring Boot
│
├── core/                           # Shared Kernel: Cấu hình chung, Security, Exception, DTO
│   ├── config/                     # Cấu hình Spring Security, CORS, JPA
│   ├── dto/                        # ApiResponse<T> chuẩn hóa toàn hệ thống
│   ├── exception/                  # GlobalExceptionHandler, BadRequestException,...
│   └── package-info.java           # @ApplicationModule(type = OPEN) - Shared Kernel
│
├── identity/                       # Bounded Context: Định danh, Tài khoản, Mã mời & Tiếp nhận
│   ├── api/                        # Public Contract: InvitationService, DTOs (@NamedInterface("api"))
│   ├── internal/                   # Private: InvitationController, Entities, Repositories, Utils
│   └── package-info.java           # @ApplicationModule(allowedDependencies = {"core", "genealogy::api"})
│
├── genealogy/                      # Bounded Context: Quản lý Dòng họ, Chi nhánh, Thành viên phả hệ
│   ├── api/                        # Public Contract: GenealogyQueryService (@NamedInterface("api"))
│   ├── internal/                   # Private: Clan, FamilyBranch, Repositories, Service Implementations
│   └── package-info.java           # @ApplicationModule(allowedDependencies = {"core"})
│
├── event/                          # Bounded Context: Quản lý Sự kiện & Contextual RBAC
│   ├── api/                        # Public Contract
│   ├── internal/                   # Private Implementation
│   └── package-info.java           # @ApplicationModule(allowedDependencies = {"core", "identity::api", "genealogy::api"})
│
├── finance/                        # Bounded Context: Tài chính, Tạm ứng, Quyết toán & Sổ cái bất biến
│   ├── api/                        # Public Contract
│   ├── internal/                   # Private Implementation
│   └── package-info.java           # @ApplicationModule(allowedDependencies = {"core", "event::api"})
│
├── contribution/                   # Bounded Context: Đóng góp Quỹ họ & Quản lý Tài sản dòng họ
│   ├── api/                        # Public Contract
│   ├── internal/                   # Private Implementation
│   └── package-info.java           # @ApplicationModule(allowedDependencies = {"core", "genealogy::api", "finance::api"})
│
└── ai/                             # Bounded Context: Trợ lý AI & Vector Store (Tách biệt tương lai)
    ├── api/                        # Public Contract
    ├── internal/                   # Private Implementation
    └── package-info.java           # @ApplicationModule(allowedDependencies = {"core"})
```

### 3.2. Quy Tắc Quy Hoạch Package Chuẩn Hóa
1. **Quy tắc Public API (`*.api.*`)**:
   - Chứa interface dịch vụ dùng để gọi giữa các module (ví dụ: `GenealogyQueryService`), public DTOs, hoặc domain events.
   - Được đánh dấu `@NamedInterface("api")` trong `package-info.java`.
   - Các module bên ngoài CHỈ ĐƯỢC PHÉP phụ thuộc vào package `api` này.
2. **Quy tắc Internal Implementation (`*.internal.*`)**:
   - Chứa Controllers, Entity JPA, Spring Data Repositories, Service Implementations, và tiện ích nội bộ.
   - **Nghiêm cấm** các module khác import trực tiếp các class trong `internal`.
   - **Xóa bỏ triệt để rò rỉ Repository**: Ví dụ `InvitationServiceImpl` (thuộc `identity`) không được phép inject `FamilyBranchRepository` (thuộc `genealogy`), mà phải gọi qua `GenealogyQueryService` trong `genealogy.api`.
3. **Shared Kernel (`com.familyfund.core`)**:
   - Được đánh dấu `type = ApplicationModule.Type.OPEN` để tất cả các module có thể truy cập các tiện ích bảo mật, DTO phản hồi chung (`ApiResponse<T>`) và ngoại lệ toàn cục.

### 3.3. Tự Động Kiểm Tra Kiến Trúc (Architecture Verification Test)
Ranh giới và quy tắc phụ thuộc được kiểm tra tự động thông qua Spring Modulith trong class test [`ArchitectureTest`](file:///d:/WorkSpace/Family-Management/family-management-backend/src/test/java/com/familyfund/ArchitectureTest.java):

```java
@Test
void verifyModularity() {
    ApplicationModules.of(FamilyfundApiApplication.class).verify();
}
```
Nếu bất kỳ thành viên nào trong nhóm vô tình gọi xuyên module hoặc truy cập vào package `internal` trái phép, lệnh build `.\gradlew.bat test` sẽ lập tức báo lỗi và chặn commit.

---

## 4. Môi Trường Vận Hành Docker (Docker Compose)

Dự án cung cấp bộ kịch bản [`docker-compose.yml`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker-compose.yml) để khởi chạy các dịch vụ phụ trợ cục bộ độc lập:

### Phân Bổ Cổng Dịch Vụ
- **5432**: PostgreSQL (`familyfund-postgres`)
- **8088**: Adminer Web GUI (`familyfund-adminer`) - Chọn cổng `8088` để tránh xung đột cổng `8080` của Spring Boot API.
- **8082**: Swagger UI (`familyfund-swagger`) - Cổng tài liệu API.
- **8080**: Ứng dụng Spring Boot Backend (`familyfund-api`).

### Cấu Hình Biến Môi Trường ([`.env`](file:///d:/WorkSpace/Family-Management/family-management-backend/.env))
```env
POSTGRES_DB=familyfund_db
POSTGRES_USER=familyfund_user
POSTGRES_PASSWORD=familyfund_secret
POSTGRES_PORT=5432
ADMINER_PORT=8088
SWAGGER_PORT=8082
```

---

## 5. Giải Quyết Ràng Buộc Phụ Thuộc Vòng (Circular Dependencies)

Trong mô hình thực tế của phân hệ Gia phả và Định danh, tồn tại 2 chu trình phụ thuộc vòng ở mức cơ sở dữ liệu:
1. `clans.founder_account_id` $\rightarrow$ `user_accounts.account_id`, nhưng `user_accounts.member_id` $\rightarrow$ `members.member_id` và `members.clan_id` $\rightarrow$ `clans.clan_id`.
2. `family_branches.representative_id` $\rightarrow$ `members.member_id`, nhưng `members.branch_id` $\rightarrow$ `family_branches.branch_id`.

### Giải Pháp Kiến Trúc Triển Khai
- Trong kịch bản DDL [`docker/postgres/init.sql`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker/postgres/init.sql) và Flyway Migrations, các bảng `clans` và `family_branches` được khởi tạo trước với cột khóa ngoại cho phép giá trị `NULL`.
- Sau khi toàn bộ các bảng `members` và `user_accounts` được tạo, các ràng buộc khóa ngoại được bổ sung bằng câu lệnh `ALTER TABLE ... ADD CONSTRAINT` kèm tùy chọn `ON DELETE SET NULL`.
- Ở tầng ứng dụng Spring Boot, quy trình đăng ký dòng họ mới sẽ chia thành 2 bước nguyên tử (atomic transaction): Tạo Clan $\rightarrow$ Tạo Member & Account $\rightarrow$ Cập nhật `founder_account_id` vào Clan.
