# Đặc Tả Kỹ Thuật & Hướng Dẫn Vận Hành US-18: Tạo hồ sơ ảo (vị trí lá) & phát hành mã liên kết vị trí dùng một lần

Tài liệu này đặc tả thiết kế, mô hình dữ liệu, giải pháp kỹ thuật, luồng nghiệp vụ và quy trình kiểm thử cho tính năng **[US-18] Trưởng tộc / Trưởng chi tạo hồ sơ ảo cho một vị trí lá trên cây phả hệ (chưa có tài khoản) và phát hành mã `PositionLinkCode` dùng một lần để người nhận vị trí tự liên kết tài khoản sau này**.

> **Điểm vào của API:** `POST /api/v1/branches/{branchId}/virtual-members`. Chi/nhánh nằm trong **đường dẫn** (không nằm trong body), cùng quy ước với US-03 (`POST /branches/{branchId}/members`), phản ánh đúng quan hệ tài nguyên `members.branch_id → family_branches.branch_id`. Hợp đồng OpenAPI nằm trong [`docker/swagger/openapi.yaml`](../../docker/swagger/openapi.yaml) (Swagger UI chạy ở cổng `8082`).
>
> **Một lời gọi, hai bản ghi:** hồ sơ ảo trong `members` và mã liên kết trong `invitation_codes` được ghi trong **cùng một transaction**. Response trả về cả hai (`member` + `positionLinkCode`) để client vẽ được vị trí trên cây và gửi link cho người nhận.

---

## 1. Tổng Quan Nghiệp Vụ (User Story & Acceptance Criteria)

### Mô tả User Story
* **Mã User Story:** `US-18`
* **Đối tượng sử dụng (Actor):** Trưởng tộc (`ROLE_CLAN_HEAD`), Trưởng chi (`ROLE_BRANCH_HEAD`), Quản trị viên (`ROLE_ADMIN`)
* **Mục tiêu (Goal):** Là người quản lý gia phả, tôi muốn tạo sẵn một **vị trí lá** trên cây (ví dụ: người đã mất, người chưa từng dùng hệ thống, con cháu ở nước ngoài) và nhận về một **link liên kết vị trí dùng một lần** để gửi cho đúng người đó.
* **Giá trị mang lại (Value):** Cây phả hệ đầy đủ ngay cả khi thành viên chưa có tài khoản (`user_accounts.member_id` còn trống). Khi người nhận mở link, hồ sơ ảo được "nhận" và gắn với tài khoản thật, toàn bộ lịch sử (sự kiện, đóng góp, tạm ứng) đã ghi trên `member_id` đó được bảo toàn (luồng redeem thuộc US-19).
* **Mức độ ưu tiên:** Bắt buộc (Must-Have)

### Tiêu chí chấp nhận (Acceptance Criteria - AC)
1. **AC-01 (Phân quyền theo vai trò):** Chỉ `CLAN_HEAD`, `BRANCH_HEAD`, `ADMIN` được gọi endpoint (`@PreAuthorize("hasAnyRole('CLAN_HEAD','BRANCH_HEAD','ADMIN')")`). Vai trò khác (ví dụ `ROLE_MEMBER`) nhận `403`; người gọi chưa xác thực nhận `401` (do `JwtAuthenticationEntryPoint` của `SecurityConfig`).
2. **AC-02 (Phân quyền theo phạm vi chi/nhánh):**
   * `ROLE_CLAN_HEAD` và `ROLE_ADMIN`: được tạo hồ sơ ảo ở **bất kỳ** chi/nhánh nào của dòng họ hiện tại.
   * `ROLE_BRANCH_HEAD`: **chỉ** được tạo trong chi/nhánh mình quản lý (`family_branches.representative_id` = `member_id` trong claim JWT). Ngoài phạm vi hoặc tài khoản chưa liên kết hồ sơ ⇒ `403`.
3. **AC-03 (Validate theo schema `members`):** Payload bị từ chối `400` nếu vi phạm: `fullName` bắt buộc/rỗng/≤ 100; `gender` ∈ {`Male`,`Female`,`Other`}; `generation` ≥ 1; `status` ∈ {`Living`,`Deceased`} (bỏ trống ⇒ `Living`); `deathDateLunar` ≤ 50; `dateOfBirth` không ở tương lai; `branchId` (path) không rỗng và ≤ 20 ký tự.
4. **AC-04 (Validate phần hạn dùng của mã liên kết):** `expiresAt` (nếu có) phải ở tương lai; `validityDays` trong khoảng `1..365`. Không truyền cả hai ⇒ mã có hiệu lực **7 ngày** (`PositionLinkServiceImpl.DEFAULT_VALIDITY_DAYS`).
5. **AC-05 (Hồ sơ ảo - không có tài khoản):** Hồ sơ được ghi với `is_placeholder = true`, **không** tạo/không cập nhật `user_accounts`. Client không được truyền `memberId`, `isPlaceholder`, `createdAt`.
6. **AC-06 (Mã liên kết vị trí dùng một lần):** Bản ghi `invitation_codes` có `invite_type = 'PositionLinkCode'`, `max_uses = 1` (server ép, không nhận từ client), `used_count = 0`, `is_active = true`, `target_member_id` = hồ sơ vừa tạo, `target_branch_id` = chi/nhánh trong đường dẫn, `created_by_account_id` = tài khoản người gọi.
7. **AC-07 (Mã sinh an toàn, không trùng):** `invite_code` do server sinh: 8 ký tự Base32 CSPRNG (bảng chữ Crockford, bỏ `0/O/1/I/L`), thử lại tối đa 5 lần khi đụng độ `PRIMARY KEY`, sau đó dựa vào ràng buộc DB.
8. **AC-08 (Bất biến thế hệ):** `generation` của hồ sơ ảo không được nhỏ hơn `generation_level` của chi/nhánh; vi phạm ⇒ `400`.
9. **AC-09 (Chống trùng hồ sơ):** Dùng chung bộ luật với US-03: trùng khi cùng `full_name` (không phân biệt hoa thường, đã trim) + `gender` + `generation` + `date_of_birth` trong **toàn dòng họ** ⇒ `409`.
10. **AC-10 (Đa dòng họ):** Mọi truy vấn đi qua `TenantContext` (`X-Clan-Id` hoặc query param `clanId`, mặc định `CLAN_DEFAULT`). Chi/nhánh của dòng họ khác ⇒ `404`.
11. **AC-11 (Toàn vẹn giao dịch):** Hồ sơ ảo + mã liên kết nằm trong **một** `@Transactional(rollbackFor = Exception.class)`. Mọi lỗi `400/403/404/409` xảy ra **trước** lần ghi đầu tiên hoặc rollback toàn bộ: không bao giờ tồn tại hồ sơ ảo "mồ côi" không có mã, cũng không có mã trỏ tới hồ sơ không tồn tại.
12. **AC-12 (Hợp đồng OpenAPI):** Endpoint được đặc tả trong [`docker/swagger/openapi.yaml`](../../docker/swagger/openapi.yaml) (`/branches/{branchId}/virtual-members`, schema `CreateVirtualMemberRequest`, `PositionLinkCode`, `VirtualMemberPosition`, `ApiResponseVirtualMember`, các mã `201/400/401/403/404/409`) và gọi thử được trên Swagger UI (`http://localhost:8082`).

---

## 2. Thiết Kế Kiến Trúc & Ranh Giới Bounded Context

### 2.1. Phân chia trách nhiệm giữa hai bounded context

| Dữ liệu | Bảng | Module sở hữu |
| :--- | :--- | :--- |
| Hồ sơ ảo | `members` (+ `family_branches`) | `com.familyfund.genealogy` |
| Mã liên kết vị trí | `invitation_codes` | `com.familyfund.identity` |

`package-info.java` khai báo `identity` chỉ được phụ thuộc `{"core", "genealogy::api"}`, còn `genealogy` chỉ được phụ thuộc `{"core"}`. Vì vậy **service điều phối bắt buộc nằm ở `identity`** (gọi sang `genealogy::api`), không thể đặt ở `genealogy` (sẽ tạo phụ thuộc vòng `genealogy → identity` làm `ArchitectureTest.verifyModularity()` fail).

```
[Client / Frontend]
        │  POST /api/v1/branches/{branchId}/virtual-members (JSON + Bearer JWT)
        ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 1. CONTROLLER (identity): PositionLinkController                         │
│    • @PreAuthorize("hasAnyRole('CLAN_HEAD','BRANCH_HEAD','ADMIN')") 403  │
│    • @Valid CreateVirtualMemberRequest                              400  │
│    • 201 Created + Location: /api/v1/invitations/{inviteCode}            │
└──────────────────────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 2. SERVICE (identity): PositionLinkServiceImpl#createVirtualMemberWith…   │
│    @Transactional(rollbackFor = Exception.class)                         │
│    1. normalize branchId (path)                              -> 400      │
│    2. currentAccountId() (bắt buộc cho created_by_account_id) -> 403     │
│    3. resolveExpiresAt (expiresAt > validityDays > 7 ngày)    -> 400     │
│    4. genealogyMemberCommandService.createVirtualMember(cmd)  -> 404/403/400/409 │
│    5. generateUniqueInviteCode() + buildPositionLinkUrl()                │
│    6. saveAndFlush(invitation_codes, PositionLinkCode, max_uses = 1)     │
└──────────────────────────────────────────────────────────────────────────┘
        │ (cross-module, chỉ qua public contract genealogy::api)
        ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 3. COMMAND SERVICE (genealogy): GenealogyMemberCommandServiceImpl         │
│    createVirtualMember(cmd)                                              │
│    • command hợp lệ + clanId == TenantContext                 -> 400     │
│    • ủy quyền cho MemberServiceImpl#createMember(branchId, request)       │
│      (khóa clans FOR UPDATE -> branch 404 -> scope 403 ->              │
│       thế hệ 400 -> trùng hồ sơ 409 -> sinh member_id -> is_placeholder)  │
│    • trả về VirtualMemberDto (member + branchName + clanId)              │
└──────────────────────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 4. PERSISTENCE                                                            │
│    • identity: InvitationCodeRepository (invitation_codes)               │
│    • genealogy: MemberRepository, FamilyBranchRepository, ClanRepository  │
│    • PostgreSQL / Flyway V1 (members, invitation_codes), V3-V5 (clan_id)  │
└──────────────────────────────────────────────────────────────────────────┘
```

### 2.2. Danh mục các file mã nguồn

| Lớp | File |
| :--- | :--- |
| Controller (identity) | `identity/internal/controller/PositionLinkController.java` |
| Service API (identity, public) | `identity/api/PositionLinkService.java` |
| Service Impl (identity) | `identity/internal/service/PositionLinkServiceImpl.java` |
| Request DTO | `identity/api/dto/CreateVirtualMemberRequest.java` |
| Response DTO | `identity/api/dto/CreateVirtualMemberResponse.java` (dùng lại `identity/api/dto/InviteCodeResponse.java`) |
| Code/URL Generator | `identity/internal/util/InviteCodeGenerator.java` (`generateCode`, `buildPositionLinkUrl`) |
| Entity/Repository (identity) | `identity/internal/entity/InvitationCode.java`, `InviteType.java`, `repository/InvitationCodeRepository.java` |
| Command API (genealogy, public) | `genealogy/api/CreateVirtualMemberCommand.java`, `genealogy/api/dto/VirtualMemberDto.java`, `genealogy/api/GenealogyMemberCommandService.java` |
| Service Impl (genealogy) | `genealogy/internal/service/GenealogyMemberCommandServiceImpl.java#createVirtualMember` → `MemberServiceImpl.java#createMember` |
| Security (core) | `core/security/CurrentUserProvider.java`, `SystemRoles.java`, `JwtPrincipal.java` |
| Exception (core) | `core/exception/GlobalExceptionHandler.java` (`400/403/404/409`), `BadRequestException`, `DuplicateResourceException` |
| Cấu hình | `application.yaml` → `app.invitation.position-link-base-url` |
| OpenAPI | `docker/swagger/openapi.yaml` |

(Các đường dẫn đều nằm dưới `src/main/java/com/familyfund/`.)

---

## 3. Thiết Kế Cơ Sở Dữ Liệu (Database Schema)

### 3.1. Bảng `members` (Flyway `V1__init_clan_genealogy_and_events_schema.sql`)
```sql
CREATE TABLE IF NOT EXISTS members (
    member_id VARCHAR(20) PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('Male', 'Female', 'Other')),
    generation INT NOT NULL CHECK (generation >= 1),
    status VARCHAR(20) NOT NULL DEFAULT 'Living' CHECK (status IN ('Living', 'Deceased')),
    death_date_lunar VARCHAR(50),
    branch_id VARCHAR(20) NOT NULL REFERENCES family_branches(branch_id) ON DELETE RESTRICT,
    is_placeholder BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
```

### 3.2. Bảng `invitation_codes` (Flyway `V1`, `clan_id` bổ sung ở `V3`/`V5`)
```sql
CREATE TABLE IF NOT EXISTS invitation_codes (
    invite_code VARCHAR(30) PRIMARY KEY,
    invite_type VARCHAR(30) NOT NULL
        CHECK (invite_type IN ('GeneralClanInvite', 'PositionLinkCode')),
    shareable_url VARCHAR(255),
    target_branch_id VARCHAR(20) REFERENCES family_branches(branch_id) ON DELETE SET NULL,
    target_member_id VARCHAR(20) UNIQUE REFERENCES members(member_id) ON DELETE CASCADE,
    max_uses INT DEFAULT 1,
    used_count INT DEFAULT 0 CHECK (used_count >= 0),
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT TRUE,
    created_by_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE RESTRICT  -- V3 + V5
);
```

> **Không thêm migration mới.** Tính năng dùng đúng 2 bảng đã có; ràng buộc nghiệp vụ "một vị trí chỉ có một mã" được DB bảo vệ bằng `target_member_id UNIQUE`, còn "dùng một lần" được bảo vệ bằng `max_uses = 1` + `used_count` + kiểm tra ở luồng redeem (`InvitationServiceImpl#validateAndGetActiveInvite`).

### 3.3. Ánh xạ Schema ⇄ Entity ⇄ Request ⇄ Response (bảng `members`)

| Cột DB | Ràng buộc DB | `Member` (Entity) | `CreateVirtualMemberRequest` | `VirtualMemberDto` (trả về trong `data.member`) |
| :--- | :--- | :--- | :--- | :--- |
| `member_id` | `VARCHAR(20) PK` | `String`, `@Id`, `length=20`, `updatable=false` | *(server sinh)* | `memberId` |
| `full_name` | `VARCHAR(100) NOT NULL` | `String`, `length=100`, `nullable=false` | `@NotBlank @Size(max=100)` | `fullName` |
| `date_of_birth` | `DATE` | `LocalDate` | `@PastOrPresent` | `dateOfBirth` |
| `gender` | `VARCHAR(10) NOT NULL CHECK` | `String`, `length=10` | `@NotBlank @Size(max=10) @Pattern(Male\|Female\|Other)` | `gender` |
| `generation` | `INT NOT NULL CHECK >= 1` | `Integer`, `nullable=false` | `@NotNull @Min(1)` | `generation` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'Living' CHECK` | `String`, mặc định `Living` | `@Size(max=20) @Pattern(Living\|Deceased)` | `status` |
| `death_date_lunar` | `VARCHAR(50)` | `String`, `length=50` | `@Size(max=50)` | `deathDateLunar` |
| `branch_id` | `VARCHAR(20) NOT NULL FK` | `@ManyToOne(optional=false) FamilyBranch` | *(path variable, `normalizeBranchId`: không rỗng, ≤ 20)* | `branchId` (+ `branchName`, `clanId`) |
| `is_placeholder` | `BOOLEAN DEFAULT FALSE` | `Boolean`, mặc định `false` | *(server đặt `true` - xem AC-05)* | `isPlaceholder` |
| `created_at` | `TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP` | `Instant`, `@CreationTimestamp` | *(server sinh)* | `createdAt` |

### 3.4. Ánh xạ Schema ⇄ Entity ⇄ Request ⇄ Response (bảng `invitation_codes`)

| Cột DB | Ràng buộc DB | `InvitationCode` (Entity) | `CreateVirtualMemberRequest` | `InviteCodeResponse` (trả về trong `data.positionLinkCode`) |
| :--- | :--- | :--- | :--- | :--- |
| `invite_code` | `VARCHAR(30) PK` | `String`, `@Id`, `length=30` | *(server sinh, 8 ký tự Base32)* | `inviteCode` |
| `invite_type` | `CHECK ('GeneralClanInvite','PositionLinkCode')` | `InviteType` (`@Enumerated(STRING)`) | *(server ép `PositionLinkCode`)* | `inviteType` |
| `clan_id` | `VARCHAR(20) NOT NULL FK` | `String`, `nullable=false` | *(từ `TenantContext`)* | `clanId` |
| `shareable_url` | `VARCHAR(255)` | `String`, `length=255` | *(server sinh từ base-url)* | `shareableUrl` |
| `target_branch_id` | `VARCHAR(20) FK` | `String`, `length=20` | *(path variable)* | `targetBranchId` (+ `targetBranchName`) |
| `target_member_id` | `VARCHAR(20) UNIQUE FK` | `String`, `length=20`, `unique=true` | *(server gán từ hồ sơ vừa tạo)* | `targetMemberId` |
| `max_uses` | `INT DEFAULT 1` | `Integer`, `@Builder.Default = 1` | *(server ép `1`)* | `maxUses` |
| `used_count` | `INT DEFAULT 0 CHECK >= 0` | `Integer`, `@Builder.Default = 0` | *(server ép `0`)* | `usedCount` |
| `expires_at` | `TIMESTAMPTZ` | `Instant` | `@Future expiresAt` \| `@Min(1) @Max(365) validityDays` | `expiresAt` |
| `is_active` | `BOOLEAN DEFAULT TRUE` | `Boolean`, `@Builder.Default = true` | *(server ép `true`)* | `isActive` |
| `created_by_account_id` | `VARCHAR(20) NOT NULL FK` | `String`, `nullable=false` | *(từ `CurrentUserProvider#currentAccountId`)* | `createdByAccountId` |
| `created_at` | `TIMESTAMPTZ` | `Instant`, `@PrePersist` | *(server sinh)* | `createdAt` |

Ứng dụng chạy với `spring.jpa.hibernate.ddl-auto: validate`, nên Hibernate đối chiếu entity với schema Flyway thật khi khởi động (`FamilyfundApiApplicationTests`, `DatabaseMigrationTest`, `PositionLinkControllerIntegrationTest`).

---

## 4. Giải Pháp Kỹ Thuật

### 4.1. Vì sao "vị trí lá" được biểu diễn bằng `members` + `is_placeholder`
* `members` **không có** cột cha–con giữa các thành viên; phân cấp duy nhất trong schema là `family_branches.parent_branch_id`. Vì vậy "lá" ở đây là **vị trí mới nhất/duy nhất còn trống dưới một chi/nhánh**, được xác định bởi 3 dấu hiệu: (a) hồ sơ mới nằm trong chi/nhánh đích, (b) `generation` không nhỏ hơn `generation_level` của chi/nhánh (không chen lên trên cấp của chi), (c) `is_placeholder = true` + chưa có `user_accounts` ⇒ vị trí "chưa được nhận".
* Việc đặt vị trí lá ở tầng chi/nhánh (thay vì thêm cột `parent_member_id`) giữ nguyên schema 3NF hiện có, đúng nguyên tắc không sửa DB khi chưa được yêu cầu.

### 4.2. Tái dùng bất biến của US-03 thay vì viết lại
`GenealogyMemberCommandServiceImpl#createVirtualMember` **không** tự chèn `members`; nó ủy quyền cho `MemberServiceImpl#createMember` — nơi đã có: khóa `clans` (`SELECT ... FOR UPDATE`) để tuần tự hóa việc chống trùng, kiểm tra chi/nhánh thuộc dòng họ, phân quyền phạm vi Trưởng chi, bất biến thế hệ, chống trùng hồ sơ, sinh `member_id` và ép `is_placeholder = true`. Nhờ vậy hai endpoint (`/members` của US-03 và `/virtual-members` của US-18) **không thể lệch luật** nhau.

Lớp command chỉ thêm hai kiểm tra riêng của US-18:
1. `command.clanId == TenantContext.getClanId()` ⇒ nếu khác thì `400` (một lệnh cho dòng họ khác không bao giờ tạo hồ sơ trong dòng họ hiện tại).
2. `command`/`branchId` rỗng ⇒ `400`.

### 4.3. Sinh mã và link liên kết vị trí
```
8K9P2X7N     ⇐ 8 ký tự CSPRNG (Crockford Base32: 23456789ABCDEFGHJKLMNPQRSTUVWXYZ)
   │
   └─ base https://app/position-link  +  "?code="  +  code
```
* `InviteCodeGenerator#generateCode()` dùng `SecureRandom` (không dùng `Random`), bỏ các ký tự dễ nhầm `0/O/1/I/L`.
* Link redeem lấy từ cấu hình riêng, **không** dùng chung với link mời chung của US-16:
  ```yaml
  app:
    invitation:
      base-url:      ${APP_INVITE_BASE_URL:http://localhost:3000/join}              # US-16
      position-link-base-url: ${APP_POSITION_LINK_BASE_URL:http://localhost:3000/position-link}  # US-18
  ```
  Base URL được chuẩn hóa (trim, bỏ `/` cuối); giá trị rỗng ⇒ fallback về mặc định.
* Ba lớp chống trùng `invite_code`:
  1. **Thuật toán:** `32^8 ≈ 1.1 × 10^12` tổ hợp.
  2. **Ứng dụng:** `PositionLinkServiceImpl#generateUniqueInviteCode()` gọi `InvitationCodeRepository#existsByInviteCode` (toàn cục vì PK toàn cục), thử tối đa `5` lần, hết lượt ⇒ `IllegalStateException` ⇒ `400`.
  3. **DB:** `PRIMARY KEY (invite_code)`; `saveAndFlush` đẩy lỗi ra ngay trong transaction để rollback sạch.

### 4.4. "Dùng một lần" được ép ở đâu
`max_uses = 1` và `used_count = 0` là **server-forced** (không có trong `CreateVirtualMemberRequest`). Luồng redeem (US-19) gọi `InvitationServiceImpl#validateAndGetActiveInvite`, hàm này đã từ chối khi `used_count >= max_uses` hoặc `expires_at` đã qua; mỗi lần redeem thành công tăng `used_count` (US-17 đã làm điều này cho `GeneralClanInvite`). Vì `target_member_id` là `UNIQUE`, một vị trí **không thể** có hai mã cùng lúc — muốn cấp lại thì phải hủy mã cũ trước (luồng thu hồi/gia hạn nằm ngoài phạm vi US-18).

### 4.5. Hạn dùng của mã
Thứ tự ưu tiên: `expiresAt` (khách truyền) → `validityDays` (khách truyền, 1..365) → mặc định **7 ngày** (`DEFAULT_VALIDITY_DAYS`). `expiresAt` không ở tương lai ⇒ `400` **trước khi** tạo hồ sơ, nên không để lại hồ sơ mồ côi.

### 4.6. Thứ tự kiểm tra
`normalizeBranchId` (400) → xác định `accountId` của người gọi (403) → `resolveExpiresAt` (400) → tạo hồ sơ ảo qua `genealogy` (404 → 403 → 400 → 409) → sinh mã (400 nếu cạn lượt) → `saveAndFlush(invitation_codes)`. Mọi kiểm tra đều chạy **trước** lần ghi đầu tiên, và cả hai lần ghi nằm trong cùng transaction.

---

## 5. Luồng Nghiệp Vụ Chi Tiết

```mermaid
sequenceDiagram
    autonumber
    actor User as Trưởng tộc / Trưởng chi / Admin
    participant C as PositionLinkController (identity)
    participant S as PositionLinkServiceImpl
    participant U as CurrentUserProvider
    participant G as GenealogyMemberCommandService (genealogy)
    participant M as MemberServiceImpl
    participant GR as InvitationCodeRepository

    User->>C: POST /api/v1/branches/{branchId}/virtual-members
    C->>C: @PreAuthorize(CLAN_HEAD|BRANCH_HEAD|ADMIN)
    alt Vai trò không hợp lệ / chưa xác thực
        C-->>User: 403 / 401
    end
    C->>C: @Valid CreateVirtualMemberRequest
    alt Payload sai schema
        C-->>User: 400 + errors
    end
    C->>S: createVirtualMemberWithPositionLink(branchId, request) [@Transactional]
    S->>S: normalizeBranchId (không rỗng, <= 20) -> 400
    S->>U: currentAccountId()
    alt Không có accountId
        S-->>User: 403
    end
    S->>S: resolveExpiresAt (expiresAt > validityDays > 7 ngày) -> 400
    S->>G: createVirtualMember(clanId, branchId, profile)
    G->>G: clanId == TenantContext ? -> 400 nếu khác
    G->>M: createMember(branchId, CreateMemberRequest)
    M->>M: SELECT ... FOR UPDATE clans ; branch thuộc clan ? -> 404
    M->>M: phạm vi vai trò (BRANCH_HEAD chỉ chi mình) -> 403
    M->>M: generation >= branch.generationLevel ? -> 400
    M->>M: chống trùng hồ sơ trong clan ? -> 409
    M->>M: sinh member_id, is_placeholder = true, saveAndFlush
    M-->>G: MemberResponse
    G-->>S: VirtualMemberDto (memberId, branchName, clanId, ...)
    loop tối đa 5 lần
        S->>GR: existsByInviteCode(candidate)
    end
    S->>GR: saveAndFlush(PositionLinkCode, max_uses = 1, used_count = 0)
    S-->>C: CreateVirtualMemberResponse(member, positionLinkCode)
    C-->>User: 201 Created + Location /api/v1/invitations/{inviteCode}
```

### Bảng mã phản hồi
| HTTP | Nguyên nhân | Exception / Nguồn |
| :---: | :--- | :--- |
| `201` | Tạo hồ sơ ảo + mã liên kết thành công | - |
| `400` | Payload sai schema, `expiresAt` không ở tương lai, `branchId` sai định dạng, thế hệ thấp hơn cấp chi, hết lượt sinh mã | `MethodArgumentNotValidException`, `BadRequestException`, `InvalidGenerationException`, `IllegalStateException` |
| `401` | Không có / sai Bearer token | `JwtAuthenticationEntryPoint` |
| `403` | Vai trò không đủ, Trưởng chi ngoài phạm vi, hoặc tài khoản không xác định được `accountId` | `AccessDeniedException`, `AuthorizationDeniedException` |
| `404` | Dòng họ/chi nhánh không tồn tại trong dòng họ hiện hành | `ResourceNotFoundException` |
| `409` | Hồ sơ trùng trong dòng họ, hoặc vi phạm toàn vẹn DB (`UNIQUE target_member_id`, PK) | `DuplicateResourceException`, `DataIntegrityViolationException` |

---

## 6. Hướng Dẫn Kiểm Thử

### 6.1. Kiểm thử Tự Động
```powershell
# Toàn bộ test của tính năng
.\gradlew.bat test --tests "com.familyfund.identity.*" --tests "com.familyfund.genealogy.*"

# Riêng US-18
.\gradlew.bat test --tests "com.familyfund.identity.internal.service.PositionLinkServiceImplTest*" `
                   --tests "com.familyfund.identity.internal.controller.PositionLinkController*" `
                   --tests "com.familyfund.identity.api.dto.CreateVirtualMemberRequestTest" `
                   --tests "com.familyfund.identity.internal.util.InviteCodeGeneratorTest"
```

| Test Class | Số Test | Mục Tiêu |
| :--- | :---: | :--- |
| `CreateVirtualMemberRequestTest` | 7 | Bean Validation khớp `members` (`NOT NULL`, `VARCHAR`, `CHECK`, ngày sinh) + luật hạn dùng (`expiresAt` tương lai, `validityDays` 1..365); khẳng định `branchId`/`memberId`/`isPlaceholder`/`maxUses`/`usedCount`/`inviteCode` **không** nằm trong payload |
| `PositionLinkServiceImplTest` (nhóm `Creation`) | 7 | Hồ sơ ảo + mã liên kết cùng được trả về; ép `max_uses = 1`, `used_count = 0`, `is_active = true`, `invite_type = PositionLinkCode`; URL theo `position-link-base-url`; hạn mặc định 7 ngày; `validityDays`/`expiresAt` override đúng thứ tự; `deathDateLunar` rỗng ⇒ `null`; thử lại khi đụng độ mã |
| `PositionLinkServiceImplTest` (nhóm `Failures`) | 6 | `branchId` rỗng/quá 20 ⇒ `400`; thiếu `accountId` ⇒ `403`; `expiresAt` quá khứ ⇒ `400`; lỗi từ genealogy (`404`) lan truyền; cạn lượt sinh mã ⇒ `IllegalStateException`; `clanId` luôn lấy từ `TenantContext` (không đọc từ body). Mọi nhánh lỗi đều `verify(never()).saveAndFlush(...)` |
| `PositionLinkControllerTest` | 7 | MockMvc standalone: `201` + header `Location` + đúng cấu trúc `data.member` / `data.positionLinkCode`; `400` (tên rỗng, gender sai, `generation` 0, `expiresAt` quá khứ, JSON hỏng); `403`; `404`; `409` |
| `PositionLinkControllerIntegrationTest` | 12 | **Full context** (`@SpringBootTest` + `@AutoConfigureMockMvc`, security filter chain thật, `TenantFilter`, DB PostgreSQL/Flyway thật, rollback sau mỗi test): `201` cho CLAN_HEAD và BRANCH_HEAD (JWT ký thật, đọc `memberId` từ claim); `401` ẩn danh; `403` cho `ROLE_MEMBER` và Trưởng chi ngoài chi; `400` payload sai / thế hệ thấp / `branchId` quá 20; `404` chi không tồn tại & chi của dòng họ khác; `409` khi trùng hồ sơ — kèm khẳng định **số bản ghi `members` và `invitation_codes` không đổi** khi lỗi, và đọc lại DB để xác nhận `is_placeholder = true`, `max_uses = 1`, `used_count = 0`, `target_member_id`, `expires_at` |
| `GenealogyMemberCommandServiceImplTest` (+3 test US-18) | 8 | `createVirtualMember` ủy quyền đúng cho `MemberService` (map command → `CreateMemberRequest`), trả `VirtualMemberDto` đầy đủ; lệnh cho dòng họ khác ⇒ `400`; command `null`/thiếu `branchId` ⇒ `400` và không tạo gì |
| `InviteCodeGeneratorTest` (+2 test US-18) | 7 | `buildPositionLinkUrl` dùng đúng base URL riêng (chuẩn hóa `/` cuối, fallback mặc định); mã rỗng ⇒ `IllegalArgumentException` |
| `ArchitectureTest` | 3 | `identity → genealogy::api` hợp lệ; không có phụ thuộc vòng sau khi thêm command mới |

### 6.2. Kịch Bản E2E Thủ Công
```bash
# 0. Lấy token (tài khoản seed ở V6: Trưởng tộc, mật khẩu 123456)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"loginIdentifier":"0901234567","password":"123456"}' | jq -r '.data.accessToken')

# 1. Trưởng tộc tạo hồ sơ ảo + mã liên kết vị trí
curl -X POST http://localhost:8080/api/v1/branches/BR_CHI_2/virtual-members \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Clan-Id: CLAN_DEFAULT" \
  -d '{
        "fullName": "Nguyễn Văn Út",
        "dateOfBirth": "2015-03-08",
        "gender": "Male",
        "generation": 4,
        "validityDays": 14
      }'
```

**Kết quả kỳ vọng (`201 Created`, header `Location: /api/v1/invitations/8K9P2X7N`):**
```json
{
  "success": true,
  "message": "Tạo hồ sơ ảo và mã liên kết vị trí thành công",
  "data": {
    "member": {
      "memberId": "MEMK4F8ZQ1M7XB3QP",
      "fullName": "Nguyễn Văn Út",
      "dateOfBirth": "2015-03-08",
      "gender": "Male",
      "generation": 4,
      "status": "Living",
      "deathDateLunar": null,
      "branchId": "BR_CHI_2",
      "branchName": "Chi 2 - Nhánh Giáp",
      "clanId": "CLAN_DEFAULT",
      "isPlaceholder": true,
      "createdAt": "2026-10-31T14:00:00Z"
    },
    "positionLinkCode": {
      "inviteCode": "8K9P2X7N",
      "clanId": "CLAN_DEFAULT",
      "clanName": "Dòng Họ Mẫu Mặc Định",
      "inviteType": "PositionLinkCode",
      "shareableUrl": "http://localhost:3000/position-link?code=8K9P2X7N",
      "targetBranchId": "BR_CHI_2",
      "targetBranchName": "Chi 2 - Nhánh Giáp",
      "targetMemberId": "MEMK4F8ZQ1M7XB3QP",
      "maxUses": 1,
      "usedCount": 0,
      "expiresAt": "2026-11-14T14:00:00Z",
      "isActive": true,
      "createdByAccountId": "ACC_TRUONG_TOC",
      "createdAt": "2026-10-31T14:00:00Z"
    }
  }
}
```

**Các kịch bản lỗi cần kiểm tra:**
| Kịch bản | Kỳ vọng |
| :--- | :--- |
| Không gửi token | `401` |
| Token của `ROLE_MEMBER` (ví dụ `0903888999`) | `403` |
| Trưởng chi gửi `branchId` của chi khác | `403` |
| `branchId` không tồn tại trong dòng họ | `404` |
| `branchId` dài hơn 20 ký tự | `400` |
| `gender: "male"`, `generation: 0`, `fullName: "   "` | `400` + `errors.{gender,generation,fullName}` |
| `expiresAt` ở quá khứ | `400` + `errors.expiresAt` |
| Gửi lại đúng payload hồ sơ đã tạo (kể cả chi khác, khác hoa thường) | `409` |
| `generation` nhỏ hơn `generation_level` của chi | `400` |

**Kiểm tra trong DB (Adminer `:8088`):**
```sql
-- Hồ sơ ảo: chưa có tài khoản, is_placeholder = true
SELECT m.member_id, m.full_name, m.is_placeholder, m.generation, m.branch_id,
       ua.account_id IS NOT NULL AS has_account
FROM members m
LEFT JOIN user_accounts ua ON ua.member_id = m.member_id
ORDER BY m.created_at DESC LIMIT 5;

-- Mã liên kết vị trí: dùng một lần, trỏ đúng hồ sơ, có hạn
SELECT invite_code, invite_type, target_member_id, target_branch_id,
       max_uses, used_count, is_active, expires_at, created_by_account_id
FROM invitation_codes
WHERE invite_type = 'PositionLinkCode'
ORDER BY created_at DESC LIMIT 5;
```
`has_account = false`, `is_placeholder = true`, `max_uses = 1`, `used_count = 0`, `expires_at` ở tương lai.

### 6.3. Kiểm Tra Hợp Đồng Trên Swagger UI
1. Khởi động backend (`.\gradlew.bat bootRun`) và Swagger UI (`docker compose up -d swagger`).
2. Mở `http://localhost:8082`, nhóm **Clan & Family Tree** → `POST /branches/{branchId}/virtual-members`.
3. **Authorize** bằng Bearer token từ `/auth/login`, bấm **Try it out**, nhập `branchId = BR_CHI_2` và body mẫu.
4. Kỳ vọng `201` với schema `ApiResponseVirtualMember` (gồm `data.member` và `data.positionLinkCode`); body sai (ví dụ `gender: "male"`) ⇒ `400` kèm `errors.gender`.

### 6.4. Ghi chú môi trường
Nếu `DatabaseMigrationTest` fail với `FlywayValidateException: Migration checksum mismatch for migration version 7` thì đó là **lệch trạng thái giữa file migration V7 trong repo và `flyway_schema_history` trên database dùng chung** (migration đã được sửa sau khi apply), không liên quan tới US-18. Cách xử lý đúng là thống nhất lại migration trong repo hoặc chạy `flyway repair` trên database đó; **không** thêm migration mới để che lỗi.

---

## 7. Phạm Vi & Các Bước Tiếp Theo

* **Đã có trong US-18:** tạo hồ sơ ảo (vị trí lá) + phát hành mã liên kết vị trí dùng một lần (kèm hạn dùng và link chia sẻ).
* **Chưa có trong US-18, đã có trong [US-19](./US-19-enter-position-link-code-and-join-automatically.md):** luồng redeem mã (`GET /api/v1/position-links/{inviteCode}` xem trước vị trí, `POST /api/v1/position-links/{inviteCode}/claim` liên kết tài khoản đang đăng ký/đã có với `members.member_id`, tăng `used_count`, đặt `is_placeholder = false`, kích hoạt tài khoản `Active` không cần phê duyệt). Mã của US-18 đã ở trạng thái sẵn sàng cho luồng đó: `invite_type = PositionLinkCode`, `target_member_id` xác định, `max_uses = 1`, có `expires_at`.
* **Chưa hỗ trợ:** cấp mã liên kết cho một hồ sơ placeholder đã tạo trước đó bằng US-03 (`POST /branches/{branchId}/members`), và cấp lại/gia hạn mã khi `target_member_id` đã có mã (ràng buộc `UNIQUE`). Nếu cần, đó là endpoint riêng, không làm thay đổi AC của US-18.
