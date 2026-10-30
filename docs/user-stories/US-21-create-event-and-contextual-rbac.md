# Đặc Tả Kỹ Thuật & Hướng Dẫn Vận Hành US-21: Khởi Tạo Sự Kiện, Quản Lý Vòng Đời & Contextual RBAC

Tài liệu này đặc tả thiết kế, mô hình dữ liệu, giải pháp kỹ thuật, luồng nghiệp vụ và quy trình kiểm thử cho tính năng **[US-21] Khởi tạo kế hoạch sự kiện mới, quản lý chuyển đổi trạng thái vòng đời sự kiện (`Draft` $\rightarrow$ `RegistrationOpen` $\rightarrow$ `RegistrationClosed` $\rightarrow$ `Ongoing` $\rightarrow$ `Ended` $\rightarrow$ `Closed`) và Middleware Contextual RBAC (`NhanSuSuKien.xacThucQuyenTheoNguCanh()`) cho phép Trưởng tộc chỉ định Ban tổ chức và Thủ quỹ gắn riêng với từng sự kiện**.

> **Điểm vào API chính:**
> - `POST /api/v1/events`: Khởi tạo sự kiện mới (`ROLE_CLAN_HEAD`, `ROLE_ADMIN`).
> - `GET /api/v1/events`: Danh sách sự kiện của dòng họ.
> - `GET /api/v1/events/{eventId}`: Chi tiết sự kiện và nhân sự ban tổ chức.
> - `PATCH /api/v1/events/{eventId}/status`: Chuyển đổi trạng thái vòng đời sự kiện.
> - `POST /api/v1/events/{eventId}/personnel`: Chỉ định Ban tổ chức và Thủ quỹ.
>
> Hợp đồng API được cập nhật trong [`docker/swagger/openapi.yaml`](../../docker/swagger/openapi.yaml) (Swagger UI cổng `8082`).

---

## 1. Tổng Quan Nghiệp Vụ (User Story & Acceptance Criteria)

### Mô tả User Story
* **Mã User Story:** `US-21`
* **Đối tượng sử dụng (Actor):** Trưởng tộc (`ROLE_CLAN_HEAD`), Quản trị viên (`ROLE_ADMIN`), Trưởng ban tổ chức sự kiện (`HeadOfOrganizer`), Thủ quỹ sự kiện (`EventTreasurer`).
* **Mục tiêu (Goal):** Là Trưởng tộc / Quản lý dòng họ, tôi muốn tạo kế hoạch tổ chức các sự kiện (giỗ tổ, chạp mã, lễ hội) kèm ngày giờ âm/dương lịch, địa điểm, dự toán ngân sách, quản lý trạng thái từng giai đoạn và chỉ định nhân sự (Ban tổ chức, Thủ quỹ) phụ trách riêng cho từng sự kiện.
* **Giá trị mang lại (Value):** Là trung tâm điều phối mọi hoạt động sinh hoạt, đăng ký hộ gia đình, tiếp nhận cúng tiến, cấp phát tạm ứng và quyết toán kinh phí dòng họ một cách minh bạch, an toàn và tự động đóng quyền khi sự kiện kết thúc.

### Tiêu chí chấp nhận (Acceptance Criteria - AC)
1. **AC-01 (Khởi tạo sự kiện):**
   - Chỉ `ROLE_CLAN_HEAD` và `ROLE_ADMIN` được phép gọi `POST /api/v1/events`.
   - Các trường bắt buộc: `eventName` ($\le 100$ ký tự), `eventDatetime` (ngày giờ dương lịch ISO-8601), `location` ($\le 255$ ký tự).
   - Các trường tùy chọn: `eventDateLunar` ($\le 50$ ký tự), `estimatedBudget` ($\ge 0$, mặc định `0.0`), `description`.
   - Trạng thái ban đầu luôn là `Draft`. Mã `eventId` tự động sinh không trùng lặp (dạng `EVT` + 8 ký tự Base36 + 6 ký tự Base32, độ dài $\le 20$).
   - Gắn chặt với `clan_id` từ `TenantContext` và `created_by` từ tài khoản đăng nhập.
2. **AC-02 (Vòng đời chuyển đổi trạng thái - Finite State Machine):**
   - Chuỗi trạng thái hợp lệ tuần tự: `Draft` $\rightarrow$ `RegistrationOpen` $\rightarrow$ `RegistrationClosed` $\rightarrow$ `Ongoing` $\rightarrow$ `Ended` $\rightarrow$ `Closed`.
   - Mọi nỗ lực nhảy cóc trạng thái hoặc đảo ngược (ví dụ `Draft` $\rightarrow$ `Ongoing`, `Ended` $\rightarrow$ `Draft`, hoặc cập nhật khi đã `Closed`) đều bị từ chối với HTTP `400 Bad Request` (`BusinessRuleException`).
   - Khi chuyển sang `Closed`: Tự động cập nhật `closed_at = NOW()`, đồng thời chuyển toàn bộ nhân sự trong `event_personnel` của sự kiện sang `is_read_only = true` để khóa vĩnh viễn quyền chỉnh sửa.
3. **AC-03 (Middleware Contextual RBAC - `NhanSuSuKien.xacThucQuyenTheoNguCanh()`):**
   - Đăng ký bean `@Component("nhanSuSuKien")` trong Spring Context cung cấp hàm:
     ```java
     boolean xacThucQuyenTheoNguCanh(String eventId, String... roles);
     ```
   - Tích hợp trực tiếp vào biểu thức bảo mật `@PreAuthorize("@nhanSuSuKien.xacThucQuyenTheoNguCanh(#eventId, 'HeadOfOrganizer')")`.
   - Quy tắc phân quyền:
     - `ROLE_ADMIN`: Toàn quyền trên mọi sự kiện.
     - `ROLE_CLAN_HEAD`: Toàn quyền trên mọi sự kiện thuộc dòng họ (`TenantContext.getClanId()`).
     - Người dùng thông thường: Phải có hồ sơ thành viên (`member_id`), phải được phân công trong bảng `event_personnel` với vai trò phù hợp và `is_read_only == false`.
     - Sự kiện đã `Closed` sẽ tự động từ chối mọi quyền ghi theo ngữ cảnh.
4. **AC-04 (Chỉ định Ban tổ chức và Thủ quỹ):**
   - Chỉ `ROLE_CLAN_HEAD` và `ROLE_ADMIN` được gọi `POST /api/v1/events/{eventId}/personnel`.
   - Ràng buộc số lượng nhân sự:
     - Tối đa 1 Trưởng ban tổ chức (`HeadOfOrganizer`).
     - Tối đa 1 Thủ quỹ sự kiện (`EventTreasurer`).
     - Không giới hạn Thành viên ban tổ chức (`OrganizerMember`).
   - Mọi `memberId` được chỉ định phải tồn tại trong cây gia phả và thuộc đúng `clan_id` hiện tại (xác minh qua `GenealogyQueryService`).
   - Không cho phép thay đổi nhân sự khi sự kiện đã `Closed`.

---

## 2. Thiết Kế Kiến Trúc & Ranh Giới Bounded Context

Tính năng thuộc Bounded Context: `com.familyfund.event`.
Giao tiếp liên module tuân thủ 100% quy tắc Spring Modulith:
- Xác thực thành viên gia phả thông qua `com.familyfund.genealogy.api.GenealogyQueryService` (Public Interface).
- Lấy thông tin tài khoản và phân quyền toàn cục thông qua `com.familyfund.core.security.CurrentUserProvider`.
- Quản lý đa dòng họ (Multi-tenancy) thông qua `com.familyfund.core.tenant.TenantContext`.

```
[Client / Mobile / Web]
        │
        ├─▶ POST /api/v1/events (Tạo sự kiện)
        ├─▶ PATCH /api/v1/events/{eventId}/status (Chuyển trạng thái)
        └─▶ POST /api/v1/events/{eventId}/personnel (Chỉ định nhân sự)
        │
        ▼
┌────────────────────────────────────────────────────────────────┐
│ CONTROLLER: EventController                                    │
│   • @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")          │
│   • @PreAuthorize("... or @nhanSuSuKien.xacThucQuyen...")      │
└────────────────────────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────────────────────────┐
│ CONTEXTUAL RBAC: NhanSuSuKienSecurity (@Component)             │
│   • Admin / Clan Head bypass                                   │
│   • Tra cứu (eventId, currentMemberId) trong event_personnel   │
│   • Chặn thao tác ghi khi event.status == Closed               │
└────────────────────────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────────────────────────┐
│ SERVICE: EventServiceImpl (@Transactional)                     │
│   • Finite State Machine: canTransitionTo()                    │
│   • Ràng buộc nhân sự: <= 1 Head, <= 1 Treasurer               │
│   • Xác thực Member qua GenealogyQueryService                  │
│   • Sinh mã duy nhất EVT...                                    │
└────────────────────────────────────────────────────────────────┘
        │
        ▼
┌────────────────────────────────────────────────────────────────┐
│ PERSISTENCE: PostgreSQL                                        │
│   • Table: events (status, estimated_budget, closed_at)        │
│   • Table: event_personnel (event_id, member_id, event_role)   │
└────────────────────────────────────────────────────────────────┘
```

### Danh Mục File Mã Nguồn
| Lớp | File |
| :--- | :--- |
| Public API Interface | `event/api/EventQueryService.java` |
| Public Enums & DTOs | `event/api/dto/EventStatus.java`, `EventRole.java`, `EventSummaryDto.java` |
| NamedInterface | `event/api/package-info.java` |
| JPA Entities | `event/internal/entity/Event.java`, `EventPersonnel.java`, `EventPersonnelId.java` |
| Repositories | `event/internal/repository/EventRepository.java`, `EventPersonnelRepository.java` |
| ID Generator | `event/internal/util/EventIdGenerator.java` |
| Security Bean | `event/internal/security/NhanSuSuKienSecurity.java` |
| Service | `event/internal/service/EventService.java`, `EventServiceImpl.java`, `EventQueryServiceImpl.java` |
| REST Controller | `event/internal/controller/EventController.java` |
| Request/Response DTOs| `event/internal/dto/request/*`, `event/internal/dto/response/*` |
| Mapper | `event/internal/mapper/EventMapper.java` |
| OpenAPI Spec | `docker/swagger/openapi.yaml` |

---

## 3. Thiết Kế Cơ Sở Dữ Liệu (Database Schema)

### Bảng `events` (Flyway V1, V3, V5)
```sql
CREATE TABLE IF NOT EXISTS events (
    event_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE RESTRICT,
    event_name VARCHAR(100) NOT NULL,
    event_datetime TIMESTAMPTZ NOT NULL,
    event_date_lunar VARCHAR(50),
    location VARCHAR(255) NOT NULL,
    estimated_budget DECIMAL(18,2) DEFAULT 0 CHECK (estimated_budget >= 0),
    description TEXT,
    status VARCHAR(25) NOT NULL DEFAULT 'Draft'
        CHECK (status IN ('Draft', 'RegistrationOpen', 'RegistrationClosed', 'Ongoing', 'Ended', 'Closed')),
    created_by VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    closed_at TIMESTAMPTZ
);
```

### Bảng `event_personnel` (Flyway V1)
```sql
CREATE TABLE IF NOT EXISTS event_personnel (
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE CASCADE,
    event_role VARCHAR(30) NOT NULL
        CHECK (event_role IN ('HeadOfOrganizer', 'OrganizerMember', 'EventTreasurer')),
    is_read_only BOOLEAN DEFAULT FALSE,
    assigned_date TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, member_id, event_role)
);
```

---

## 4. Hướng Dẫn Gọi REST API & Kịch Bản cURL

### 4.1. Khởi Tạo Sự Kiện Mới
```bash
curl -X POST "http://localhost:8080/api/v1/events" \
  -H "Authorization: Bearer $CLAN_HEAD_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "eventName": "Lễ Giỗ Tổ Chi 1 Bính Ngọ 2026",
    "eventDatetime": "2026-04-26T08:00:00Z",
    "eventDateLunar": "10/03 Bính Ngọ",
    "location": "Từ đường Chi 1, thôn Phú Mẫn, Yên Phong, Bắc Ninh",
    "estimatedBudget": 45000000.00,
    "description": "Giỗ tổ thường niên toàn thể con cháu quy tụ"
  }'
```

### 4.2. Chỉ Định Ban Tổ Chức & Thủ Quỹ Sự Kiện
```bash
curl -X POST "http://localhost:8080/api/v1/events/EVT12345678/personnel" \
  -H "Authorization: Bearer $CLAN_HEAD_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "personnel": [
      {
        "memberId": "MEM10000001",
        "role": "HeadOfOrganizer"
      },
      {
        "memberId": "MEM10000002",
        "role": "EventTreasurer"
      },
      {
        "memberId": "MEM10000003",
        "role": "OrganizerMember"
      }
    ]
  }'
```

### 4.3. Chuyển Đổi Trạng Thái Vòng Đời Sự Kiện
```bash
# Mở đăng ký hộ gia đình (Trưởng ban tổ chức hoặc Trưởng tộc thực hiện)
curl -X PATCH "http://localhost:8080/api/v1/events/EVT12345678/status" \
  -H "Authorization: Bearer $HEAD_OF_ORGANIZER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "RegistrationOpen"
  }'
```

---

## 5. Kết Quả Kiểm Thử & Xác Minh Tự Động

Toàn bộ các tầng kiến trúc đã được kiểm thử tự động toàn diện:
```powershell
# 1. Kiểm thử kiểm soát kiến trúc Spring Modulith
cd family-management-backend; .\gradlew.bat test --tests "com.familyfund.ArchitectureTest"

# 2. Kiểm thử riêng module sự kiện (Security, Service, Controller)
cd family-management-backend; .\gradlew.bat test --tests "com.familyfund.event.*"

# 3. Chạy toàn bộ test suite hệ thống (223 tests)
cd family-management-backend; .\gradlew.bat test
```

### Kết Quả Đạt Được:
- **ArchitectureTest**: Vượt qua 100%, ranh giới modulith được bảo vệ nguyên vẹn.
- **Unit & MockMvc Tests**: 100% tests của `com.familyfund.event.*` đều passed.
- **Toàn bộ dự án**: **223/223 tests** thành công 100% không có lỗi.
