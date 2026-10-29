# Trạng Thái Triển Khai Dự Án (Project Status & Roadmap)

Tài liệu này theo dõi tiến độ kỹ thuật, các hạng mục đã hoàn thành, kết quả xác minh thực tế và kế hoạch thực hiện các bước tiếp theo của dự án **Family Management Backend** (`familyfund-api`).

---

## 1. Bảng Trạng Thái Triển Khai Nhiệm Vụ (Task Progress)

| Mã Task | Tên Nhiệm Vụ & Phạm Vi Kỹ Thuật | Trạng Thái | Minh Chứng & Kết Quả Xác Minh |
| :--- | :--- | :--- | :--- |
| **US-10** | **Cấu Hình JWT Tokens, Claims Quyền Hạn & Middleware Gán ROLE_CLAN_HEAD (Task #9 & #10)**<br/>- **Task 1:** Tích hợp thư viện JJWT 0.12.6, cấu hình Access Token 1h & Refresh Token 7d trong application.yaml<br/>- **Task 2:** Component JwtTokenProvider phát sinh và xác thực token với custom claims: sub, clanId, memberId, fullName, roles<br/>- **Task 3:** Middleware JwtAuthenticationFilter: Tự động trích xuất token, nạp ROLE_CLAN_HEAD vào SecurityContext và đồng bộ clanId vào TenantContext<br/>- **Task 4:** AuthServiceImpl & AuthController: API đăng nhập (/api/v1/auth/login) và API làm mới token (/api/v1/auth/refresh-token)<br/>- **Task 5:** SecurityConfig: addFilterBefore, xử lý 401 Unauthorized chuẩn JSON envelope, bảo vệ tài nguyên theo chuẩn REST | **Đã Hoàn Thành (Implemented & Verified)** | - 100% automated tests passed (**14 new/updated tests**, nâng tổng số lên **86/86 tests** pass 100%).<br/>- 100% tuân thủ kiến trúc Spring Modulith (ArchitectureTest).<br/>- Tài liệu đặc tả kỹ thuật: [docs/user-stories/US-10-jwt-authentication-and-claims.md](./user-stories/US-10-jwt-authentication-and-claims.md). |
| **US-01** | **Đăng Ký Người Khởi Tạo & Tạo Cây Gia Phả Mới**<br/>- **Task 1:** Entity UserAccount, SystemRole, enum AccountStatus, Repositories (identity)<br/>- **Task 2:** Public interface GenealogyBootstrapService (genealogy::api) khởi tạo nguyên tử Clan, Root branch, Thủy tổ, Creator member<br/>- **Task 3:** CreatorRegistrationService: Chống trùng SĐT/email, băm mật khẩu BCryptPasswordEncoder, cấp vai trò ROLE_CLAN_HEAD<br/>- **Task 4:** REST Controller (/api/v1/auth/register-creator), HTTP 201 Created, tài liệu OpenAPI và User Story | **Đã Hoàn Thành (Implemented & Verified)** | - 100% automated tests passed (**8 new tests**, nâng tổng số lên **78/78 tests** pass 100%).<br/>- 100% tuân thủ kiến trúc Spring Modulith (ArchitectureTest).<br/>- Tài liệu đặc tả kỹ thuật: [docs/user-stories/US-01-creator-registration-and-clan-creation.md](./user-stories/US-01-creator-registration-and-clan-creation.md). |
| :--- | :--- | :--- | :--- |
| **BE-Task 1** | **Thiết kế Database Schema & Môi trường Docker / Neon Cloud**<br/>- Thiết kế 23 bảng thuộc các phân hệ cốt lõi<br/>- Khởi tạo script DDL [`docker/postgres/init.sql`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker/postgres/init.sql)<br/>- Khởi tạo [`docker-compose.yml`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker-compose.yml) (Adminer :8088, Swagger :8082)<br/>- Kết nối CSDL Neon Serverless PostgreSQL qua SSL<br/>- Cung cấp tài liệu [`docker/swagger/openapi.yaml`](file:///d:/WorkSpace/Family-Management/family-management-backend/docker/swagger/openapi.yaml)<br/>- Cập nhật [`application.yaml`](file:///d:/WorkSpace/Family-Management/family-management-backend/src/main/resources/application.yaml) & [`.env`](file:///d:/WorkSpace/Family-Management/family-management-backend/.env) | **Đã Hoàn Thành (Implemented)** | - `docker compose config` kiểm tra cấu hình thành công 100%.<br/>- `.\gradlew.bat clean build -x test` thành công, tạo file JAR `familyfund-api-0.0.1-SNAPSHOT.jar`.<br/>- DDL script và Flyway migrations (V1 $\rightarrow$ V4) bao phủ 23 bảng, giải quyết 2 vòng phụ thuộc và hỗ trợ `pgvector`. |
| **US-03** | **Tạo Hồ Sơ Thành Viên Gia Phả (Member), Chống Trùng & Phân Quyền Theo Chi/Nhánh**<br/>- Entity `Member` ánh xạ 1-1 bảng `members` (không dùng enum để khớp `CHECK`)<br/>- DTOs: `CreateMemberRequest` (Bean Validation khớp DB, **không** chứa `branchId`), `MemberResponse`, `MemberMapper` phân giải `branchName`/`clanId`<br/>- REST: `POST /api/v1/branches/{branchId}/members` ⇒ `201 Created`; `branchId` lấy từ path variable, kiểm tra trim/không rỗng/≤ 20 ở service (`400`)<br/>- OpenAPI: đặc tả `/branches/{branchId}/members` + `components.schemas.*` trong `docker/swagger/openapi.yaml` (Swagger UI `:8082`)<br/>- `MemberIdGenerator` (`MEM` + Base36 timestamp + CSPRNG, ≤ 20 ký tự) + `existsByMemberId` retry + PK bảo vệ<br/>- `MemberService` (`@Transactional`): khóa hàng `clans` (`FOR UPDATE`), kiểm tra branch theo clan, bất biến thế hệ, chống trùng hồ sơ (`409`), luôn `is_placeholder = true`<br/>- Phân quyền: `CLAN_HEAD`/`ADMIN` mọi chi; `BRANCH_HEAD` chỉ chi mình là `representative_id` (`403` nếu ngoài phạm vi)<br/>- `core.security`: `SystemRoles`, `CurrentUserProvider`, `MemberLinkedPrincipal` | **Đã Hoàn Thành (Implemented & Verified)** | - Toàn bộ **154/154 tests** pass, gồm `MemberServiceImplTest` (17 unit), `MemberServiceIntegrationTest` (4, trên schema Flyway/PostgreSQL thật, rollback), `MemberControllerIntegrationTest` (16 integration + security qua full context: `400`/`401`/`403`/`404`/`409`, header `X-Clan-Id`, trong đó 2 test đi qua `JwtAuthenticationFilter` với JWT ký thật để xác nhận phạm vi `BRANCH_HEAD` lấy `memberId` từ claim `memberId` qua `JwtPrincipal`), `MemberControllerTest` (7 MockMvc), `CreateMemberRequestTest` (6), `CurrentUserProviderTest` (2), `GlobalExceptionHandlerIntegrationTest` (4).<br/>- `.\gradlew.bat build --rerun-tasks` thành công (compile + test + JAR).<br/>- Kiểm chứng thực tế trên server `:8080`: ẩn danh ⇒ `403`, JSON hỏng ⇒ `400`, body sai ⇒ `400` kèm `errors`, route lạ ⇒ `404`, sai method ⇒ `405`.<br/>- `ArchitectureTest` vẫn hợp lệ.<br/>- Phụ thuộc BE-Task 3: tầng xác thực phải cung cấp principal hiện thực `MemberLinkedPrincipal`.<br/>- Tài liệu: [`docs/user-stories/US-03-create-member-profile.md`](./user-stories/US-03-create-member-profile.md). |
| **US-04** | **Quản Lý Chi Phái, Phân Nhánh & Cấu Trúc Phân Cấp Gia Phả**<br/>- **Task 1:** Entity `FamilyBranch`, quan hệ phân cấp Adjacency List, DTOs (Records), `BranchMapper` (MapStruct)<br/>- **Task 2:** Service layer: Root branch, Child branch, Reparenting cascade thế hệ, Chống chu trình `kiemTraChuTrinh`<br/>- **Task 3:** Dựng cây in-memory tối ưu N+1 (`getBranchTree`), Multi-tenant scoping qua `TenantContext` & `TenantFilter`<br/>- **Task 4:** Chuẩn hóa dữ liệu 3NF (Flyway `V4__drop_clan_name_from_family_branches.sql`), loại bỏ `CascadeType.REMOVE` an toàn dữ liệu | **Đã Hoàn Thành (Implemented & Verified)** | - 100% automated tests passed (**39 unit tests** trong module `genealogy`).<br/>- Triệt tiêu N+1 database queries khi dựng cây phả hệ bằng in-memory assembly.<br/>- Tài liệu đặc tả kỹ thuật: [`docs/user-stories/US-04-child-branches-and-hierarchy.md`](./user-stories/US-04-child-branches-and-hierarchy.md). |
| **US-16** | **Tạo Mã Mời (Invite Code) Chung Của Gia Phả**<br/>- **Task 1:** Entity `InvitationCode`, Enums, DTOs, Repository, Generator CSPRNG Base32 (`9e7de92`)<br/>- **Task 2:** Service layer, Business Invariants, Collision retry, Unit tests (`8a6705e`)<br/>- **Task 3:** REST Controller (`/api/v1/invitations`), Method Security, Exception handling, MockMvc tests, OpenAPI spec (`8fb026a`) | **Đã Hoàn Thành (Implemented & Verified)** | - 100% automated tests passed (**22 tests** trong module `identity`).<br/>- Đã tái cấu trúc vào bounded context `com.familyfund.identity`.<br/>- Tài liệu đặc tả & hướng dẫn kiểm thử E2E: [`docs/user-stories/US-16-general-clan-invite.md`](file:///d:/WorkSpace/Family-Management/family-management-backend/docs/user-stories/US-16-general-clan-invite.md). |
| **US-21** | **Khởi Tạo Sự Kiện, Quản Lý Vòng Đời & Contextual RBAC (`NhanSuSuKien`)**<br/>- **Task 1:** Entity `Event`, `EventPersonnel`, `EventPersonnelId` (IdClass), Enums `EventStatus`, `EventRole`, Repositories, ID Generator `EVT...`<br/>- **Task 2:** Service layer: Finite State Machine (`Draft` $\rightarrow$ `RegistrationOpen` $\rightarrow$ `RegistrationClosed` $\rightarrow$ `Ongoing` $\rightarrow$ `Ended` $\rightarrow$ `Closed`), auto read-only when closed, gán nhân sự (tối đa 1 Trưởng ban, 1 Thủ quỹ, xác thực qua `GenealogyQueryService`)<br/>- **Task 3:** Middleware Contextual RBAC `@Component("nhanSuSuKien")` với method `xacThucQuyenTheoNguCanh()`, tích hợp `@PreAuthorize`<br/>- **Task 4:** REST Controller (`/api/v1/events`), OpenAPI 3.0 spec trong `docker/swagger/openapi.yaml`, Unit & MockMvc tests | **Đã Hoàn Thành (Implemented & Verified)** | - 100% automated tests passed (**223/223 tests** trong toàn bộ dự án).<br/>- `ArchitectureTest` Spring Modulith pass 100% không vi phạm ranh giới module.<br/>- Đặc tả kỹ thuật đầy đủ: [`docs/user-stories/US-21-create-event-and-contextual-rbac.md`](./user-stories/US-21-create-event-and-contextual-rbac.md). |
| **US-22** | **Tích Hợp Dịch Vụ Thông Báo Khi Mở Sự Kiện & Cập Nhật Lịch Trình**<br/>- **Task 1:** Entity `EventNotification`, Repository, Generator CSPRNG `NTF...`, Domain Events (`EventOpenedDomainEvent`, `EventScheduleChangedDomainEvent`) trong `com.familyfund.event.api.event`<br/>- **Task 2:** Service layer: Tự động so sánh diff ngày giờ/địa điểm trong `updateEvent()`, phát event khi `RegistrationOpen`, API `getEventNotifications()`<br/>- **Task 3:** Event Listener bất đồng bộ (`@Async @EventListener`), tích hợp interface `NotificationSender` với adapter ghi log `LoggingNotificationSender`<br/>- **Task 4:** REST Controller (`PUT /api/v1/events/{eventId}`, `GET /api/v1/events/{eventId}/notifications`), OpenAPI 3.0 spec, Unit & MockMvc tests | **Đã Hoàn Thành (Implemented & Verified)** | - 100% automated tests passed (**233/233 tests** trong toàn bộ dự án, bổ sung 10 test cases mới).<br/>- `ArchitectureTest` Spring Modulith pass 100% bảo toàn ranh giới bounded context.<br/>- Đặc tả kỹ thuật đầy đủ: [`docs/user-stories/US-22-event-notification-and-reschedule.md`](./user-stories/US-22-event-notification-and-reschedule.md). |
| **US-09** | **Ghi Nhận Khoản Đóng Góp Tiền Mặt/Chuyển Khoản & Cấp Phiếu Thu Điện Tử**<br/>- **Task #68:** API `POST /api/v1/contributions/cash` ghi nhận tiền mặt / chuyển khoản, Contextual RBAC `@nhanSuSuKien`<br/>- **Task #69:** Tự động sinh mã phiếu thu duy nhất (`ReceiptNumberGenerator` format `PT-YYYYMMDD-XXXXX`) & mã đóng góp (`CTB...`)<br/>- **Task #70:** Tự động ghi nhận tỷ lệ phân bổ vào `contribution_member_details` cho 1 người hoặc nhiều con cháu đồng đóng góp (làm dữ liệu chuẩn cho US-11)<br/>- **Task #71:** Giao diện bàn tiếp nhận Thủ quỹ (`TiepNhanDongGopUI` - Flutter) | 🔄 **Đang Triển Khai (In Progress - Backend)** | - Đã đặc tả kỹ thuật chi tiết: [`docs/user-stories/US-09-cash-contribution-and-e-receipt.md`](./user-stories/US-09-cash-contribution-and-e-receipt.md). |
| **ARCH-01** | **Tái Cấu Trúc Kiến Trúc Modular Monolith & Spring Modulith**<br/>- Khóa cứng Java toolchain lên Java 21 LTS.<br/>- Tích hợp Spring Modulith 2.1.1 (`spring-modulith-bom:2.1.1`, core, test).<br/>- Quy hoạch lại package theo Domain Bounded Contexts: `identity`, `genealogy`, `event`, `finance`, `contribution`, `ai`, `core`.<br/>- Tách `api` (Public Contract) và `internal` (Private Implementation).<br/>- Sát nhập tính năng lời mời vào bounded context `identity`.<br/>- Triệt tiêu rò rỉ repository giữa các module: Trích xuất `GenealogyQueryService` thay vì gọi trực tiếp `FamilyBranchRepository`.<br/>- Tích hợp `TenantFilter` và `TenantContext` xử lý multi-tenancy tự động qua header `X-Clan-Id` hoặc query param `clanId`.<br/>- Viết kiểm thử kiến trúc tự động bằng Spring Modulith (`ArchitectureTest`). | **Đã Hoàn Thành (Implemented & Verified)** | - `ArchitectureTest.verifyModularity()` vượt qua thành công 100%.<br/>- Toàn bộ **70/70 tests** trong toàn bộ test suite đều pass (100% success rate).<br/>- Tài liệu kiến trúc chuẩn hóa: [`docs/architecture/01-system-architecture.md`](file:///d:/WorkSpace/Family-Management/family-management-backend/docs/architecture/01-system-architecture.md). |
| **CI-01** | **Thiết Lập GitHub Actions CI Pipeline & Chuẩn Hóa Kiểm Thử Database**<br/>- Khởi tạo pipeline tự động hóa [`.github/workflows/ci.yml`](file:///d:/WorkSpace/Family-Management/family-management-backend/.github/workflows/ci.yml) trên Ubuntu runner.<br/>- Cấu hình service container `pgvector/pgvector:pg16` với health-check cụ thể (`pg_isready -U test_user -d familyfund_test`).<br/>- Cấu hình concurrency tối ưu theo PR & Ref tránh chạy trùng lặp.<br/>- Đóng gói ứng dụng và chạy toàn bộ test suite bằng lệnh chuẩn mực `./gradlew build`.<br/>- Tự động lưu trữ test reports với `if-no-files-found: ignore`.<br/>- Khắc phục quyền execute của `gradlew` trên Git index (`100755`).<br/>- Bổ sung assertion thực tế cho [`DatabaseMigrationTest.java`](file:///d:/WorkSpace/Family-Management/family-management-backend/src/test/java/com/familyfund/DatabaseMigrationTest.java) (xác minh $\ge$ 24 domain tables và 4 seeded system roles). | **Đã Hoàn Thành (Implemented & Verified)** | - Toàn bộ **70/70 tests** vượt qua thành công 100% với assertion kiểm tra CSDL thực tế.<br/>- `./gradlew build` biên dịch, chạy test và tạo JAR thành công trong 1m11s.<br/>- File JAR độc lập: `familyfund-api-0.0.1-SNAPSHOT.jar` (71.2 MB). |
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
   - Chạy thành công toàn bộ **154/154 kiểm thử tự động** với tỷ lệ pass 100% (`.\gradlew.bat test --rerun-tasks` / `.\gradlew.bat build --rerun-tasks`):
     - `ArchitectureTest`: 3 tests
     - `TenantFilterTest`: 3 tests
     - `DatabaseMigrationTest`: 2 tests (xác minh thực tế $\ge$ 24 domain tables & 4 system roles)
     - `FamilyfundApiApplicationTests`: 1 test
     - `BranchMapperTest`: 3 tests
     - `GenealogyHierarchyTest`: 15 tests
     - `GenealogyMultiTenantSecurityTest`: 5 tests
     - `GenealogyQueryServiceImplTest`: 5 tests
     - `GenealogyRootBranchTest`: 8 tests
     - `GenealogyValidatorTest`: 5 tests
     - `CreateGeneralInviteRequestTest`: 3 tests
     - `InvitationControllerTest`: 6 tests
     - `InvitationServiceTest`: 8 tests
     - `InviteCodeGeneratorTest`: 5 tests
     - `CreateMemberRequestTest`: 6 tests
     - `MemberIdGeneratorTest`: 2 tests
     - `MemberMapperTest`: 1 test
     - `MemberServiceImplTest`: 17 tests (6 phạm vi vai trò + 11 nghiệp vụ)
     - `MemberServiceIntegrationTest`: 4 tests (schema Flyway/PostgreSQL thật)
     - `MemberControllerTest`: 7 tests (MockMvc standalone)
     - `MemberControllerIntegrationTest`: 16 tests (integration + security, full context, gồm 2 test đi qua `JwtAuthenticationFilter` với token ký thật)
     - `CurrentUserProviderTest`: 2 tests
     - `GlobalExceptionHandlerIntegrationTest`: 4 tests (JSON hỏng `400`, route lạ `404`, sai method `405`, sai Content-Type `415`; đều gửi kèm principal hợp lệ để không bị chặn `401` trước MVC)
     - `JwtAuthenticationFilterTest`: 3 tests
     - `JwtTokenProviderTest`: 3 tests
     - `GenealogyBootstrapServiceImplTest`: 2 tests
     - `AuthControllerTest`: 6 tests
     - `AuthServiceImplTest`: 6 tests
     - `CreatorRegistrationServiceImplTest`: 3 tests
3. **Quy Trình Continuous Integration (GitHub Actions CI)**:
   - Pipeline tự động hóa [`.github/workflows/ci.yml`](file:///d:/WorkSpace/Family-Management/family-management-backend/.github/workflows/ci.yml) được cấu hình chuẩn mực:
     - Health-check chỉ định rõ user và database: `--health-cmd "pg_isready -U test_user -d familyfund_test"`.
     - Sử dụng service container `pgvector/pgvector:pg16` cô lập hoàn toàn môi trường test khỏi Cloud DB.
     - Khóa quyền thực thi `100755` cho `gradlew` trực tiếp trên Git index.
     - Tận dụng cache của Gradle để tăng tốc độ build.
     - Cơ chế concurrency hủy job cũ an toàn theo PR và Branch ref.
4. **Cơ Chế Đa Dòng Họ Minh Bạch (Transparent Multi-Tenancy)**:
   - Tích hợp `TenantFilter` (`OncePerRequestFilter`) và `TenantContext` (`ThreadLocal`) trong `com.familyfund.core.tenant`.
   - Tự động phân giải `clanId` từ HTTP header `X-Clan-Id` hoặc query param `clanId`, fallback an toàn về `CLAN_DEFAULT`.
   - Tinh gọn Controller layer, không cần thủ công trích xuất tenant ở từng endpoint.
5. **Mô hình DDL Cơ sở dữ liệu & Flyway Migrations**:
   - Chuẩn hóa CSDL qua Flyway Migrations V1 $\rightarrow$ V5 (24 bảng nghiệp vụ).
   - Xử lý mượt mà 2 trường hợp phụ thuộc vòng (`clans` $\leftrightarrow$ `user_accounts`, `family_branches` $\leftrightarrow$ `members`).
   - Migration V4 hoàn tất chuẩn hóa 3NF: Loại bỏ `clan_name` khỏi `family_branches`, đưa `clans` thành Single Source of Truth.
   - Migration V5 hoàn tất việc áp đặt `NOT NULL` và `DEFAULT 'CLAN_DEFAULT'` cho `clan_id` trên mọi bảng đa dòng họ.
6. **Cấu hình Hạ Tầng (Neon Cloud & Docker Compose)**:
   - Kết nối trực tiếp CSDL đám mây **Neon Serverless PostgreSQL** hỗ trợ SSL và pgvector qua cấu hình biến môi trường [`.env`](file:///d:/WorkSpace/Family-Management/family-management-backend/.env).
   - Docker Compose cung cấp môi trường Swagger UI (cổng `8082`) và Adminer Web DB GUI (cổng `8088`).
7. **Bảo mật & Biến môi trường**:
   - Đã bổ sung `.env` và thư mục `bin/` vào file [`.gitignore`](file:///d:/WorkSpace/Family-Management/family-management-backend/.gitignore).
8. **Bảo Toàn Dữ Liệu Phả Hệ & Chống Chu Trình (US-04)**:
   - Loại bỏ `CascadeType.ALL` trên `FamilyBranch` để bảo vệ hồ sơ thành viên không bị xóa nhầm.
   - Thuật toán `kiemTraChuTrinh` ngăn chặn vòng lặp cha-con và bảo toàn tính toàn vẹn của cây phả hệ.
   - Dựng cây đệ quy in-memory loại bỏ hoàn toàn N+1 queries.
9. **Sinh Mã Mời An Toàn Mật Mã (US-16)**:
   - Sinh mã 8 ký tự Base32 CSPRNG loại trừ ký tự dễ nhầm lẫn.
   - Cơ chế thử lại tự động khi đụng độ khóa (Collision Retry).

---

## 3. Những Điểm Cần Lưu Ý & Các Bước Tiếp Theo

- **Quy tắc phát triển tính năng mới**:
  - Đặt DTOs và Interfaces giao tiếp liên module trong package `<module>.api` và khai báo `@NamedInterface("api")`.
  - Đặt Entity, Controller, Repository và Service Implementation trong `<module>.internal`.
  - Luôn chạy `.\gradlew.bat test` hoặc `.\gradlew.bat build` để đảm bảo `ArchitectureTest` và toàn bộ test suite vượt qua trước khi push code lên GitHub.
