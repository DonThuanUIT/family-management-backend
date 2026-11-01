# Đặc Tả Kỹ Thuật & Hướng Dẫn Vận Hành US-06: Đăng Ký Tham Dự Sự Kiện & Thống Kê Cỗ Bàn

Tài liệu này đặc tả thiết kế kiến trúc, mô hình dữ liệu, cơ chế phân quyền ngữ cảnh, quy trình kiểm soát sửa/hủy 2 bước và kịch bản kiểm thử cho tính năng **[US-06] Với vai trò Thành viên / Người đại diện hộ gia đình, tôi muốn đăng ký số lượng người tham dự, nhu cầu ăn uống (chay/mặn) và phương tiện di chuyển khi sự kiện đang mở tiếp nhận đăng ký, để Ban tổ chức có thể nắm bắt chính xác số lượng chuẩn bị khánh tiết, cỗ bàn chu đáo theo thời gian thực**.

> **Điểm vào API chính:**
> - `POST /api/v1/events/{eventId}/registrations`: Nộp biểu mẫu đăng ký tham gia sự kiện.
> - `GET /api/v1/events/{eventId}/registrations/summary`: Thống kê tổng hợp cộng dồn cỗ bàn và người tham dự theo thời gian thực.
> - `GET /api/v1/events/{eventId}/registrations`: Tra cứu danh sách các hộ gia đình đăng ký (phân trang, dành cho Ban tổ chức).
> - `GET /api/v1/events/{eventId}/registrations/me`: Xem thông tin đăng ký của chính hộ gia đình người đăng nhập.
> - `GET /api/v1/events/{eventId}/registrations/{registrationId}`: Xem chi tiết một lượt đăng ký.
> - `POST /api/v1/events/{eventId}/registrations/{registrationId}/request-modification`: Người đại diện gửi đề xuất thay đổi cỗ bàn/người tham dự (`PendingModification`).
> - `POST /api/v1/events/{eventId}/registrations/{registrationId}/request-cancellation`: Người đại diện gửi đề xuất hủy đăng ký (`PendingCancellation`).
> - `POST /api/v1/events/{eventId}/registrations/{registrationId}/review`: Ban tổ chức xét duyệt (chấp thuận hoặc từ chối) yêu cầu sửa/hủy.
>
> Hợp đồng API được cập nhật trong [`docker/swagger/openapi.yaml`](../../docker/swagger/openapi.yaml) (Swagger UI cổng `8082`).

---

## 1. Tổng Quan Nghiệp Vụ (User Story & Acceptance Criteria)

### Mô tả User Story
* **Mã User Story:** `US-06`
* **Phân hệ:** Đăng ký tham dự (Event Attendance Registration)
* **Đối tượng sử dụng (Actors):**
  - Thành viên / Người đại diện hộ gia đình (`ROLE_MEMBER`, `ROLE_BRANCH_HEAD`).
  - Ban tổ chức sự kiện (`HeadOfOrganizer`, `OrganizerMember`, `EventTreasurer`).
  - Trưởng tộc (`ROLE_CLAN_HEAD`), Quản trị viên hệ thống (`ROLE_ADMIN`).
* **Tiền điều kiện (Preconditions):**
  1. Sự kiện đang ở trạng thái mở tiếp nhận đăng ký (`event.status == RegistrationOpen`).
  2. Người đại diện phải là thành viên hợp lệ trong cây gia phả thuộc cùng dòng họ (`clan_id`).
  3. Mỗi hộ gia đình / người đại diện chỉ có tối đa một lượt đăng ký đang hoạt động (`status != Cancelled`).

---

## 2. Tiêu Chí Chấp Nhận (Acceptance Criteria - AC)

1. **AC-01 (Nộp biểu mẫu đăng ký tham gia):**
   - Sự kiện phải ở trạng thái `RegistrationOpen`. Nếu sự kiện ở trạng thái khác (`Draft`, `RegistrationClosed`, `Ongoing`, `Ended`, `Closed`), hệ thống trả về `400 Bad Request` (`BusinessRuleException`).
   - Tổng số người tham dự `totalAttendees >= 1`.
   - Bất biến khẩu phần ăn: `vegetarianMeals + savoryMeals <= totalAttendees`.
   - Chống trùng lặp: Nếu hộ gia đình của thành viên đại diện đã có lượt đăng ký chưa bị hủy (`status != Cancelled`), hệ thống trả về `409 Conflict` (`DuplicateResourceException`).
   - Danh sách thành viên gia đình đi kèm (`attendingMemberIds`): nếu có cung cấp, mỗi thành viên phải thuộc cùng dòng họ trong cây gia phả và số lượng không vượt quá `totalAttendees`.
   - Sau khi lưu thành công, trạng thái ban đầu là `Confirmed`. Hệ thống phát hành `FamilyRegistrationSubmittedDomainEvent`.

2. **AC-02 (Cộng dồn số liệu cỗ bàn theo thời gian thực):**
   - Hệ thống cung cấp endpoint `GET /api/v1/events/{eventId}/registrations/summary` tính toán trực tiếp trên chỉ mục cơ sở dữ liệu.
   - Chỉ tính các lượt đăng ký chưa bị hủy (`status != Cancelled`).
   - Trả về: `totalRegistrations`, `totalAttendees`, `totalVegetarianMeals`, `totalSavoryMeals`.

3. **AC-03 (Quy trình 2 bước khi Đề xuất Sửa đổi & Hủy):**
   - Thành viên đại diện gửi yêu cầu sửa đổi (`POST .../request-modification`):
     - Lượt đăng ký chuyển trạng thái sang `PendingModification`.
     - Lưu trữ các thông số đề xuất: `proposedTotalAttendees`, `proposedVegetarianMeals`, `proposedSavoryMeals`, `modificationReason`.
     - Số liệu cộng dồn hoạt động vẫn giữ nguyên các con số cũ cho đến khi Ban tổ chức phê duyệt.
     - Phát hành `RegistrationModificationRequestedDomainEvent` ("MODIFICATION").
   - Thành viên đại diện gửi yêu cầu hủy (`POST .../request-cancellation`):
     - Lượt đăng ký chuyển trạng thái sang `PendingCancellation` kèm `cancellationReason`.
     - Phát hành `RegistrationModificationRequestedDomainEvent` ("CANCELLATION").

4. **AC-04 (Ban tổ chức xét duyệt yêu cầu):**
   - Chỉ `ROLE_ADMIN`, `ROLE_CLAN_HEAD` hoặc nhân sự có vai trò ngữ cảnh `HeadOfOrganizer` mới có quyền gọi `POST .../review`.
   - Nếu `approved = true`:
     - Với `PendingModification`: Cập nhật `totalAttendees`, `vegetarianMeals`, `savoryMeals` sang giá trị đề xuất; chuyển trạng thái về `Confirmed`.
     - Với `PendingCancellation`: Chuyển trạng thái sang `Cancelled`. Lượt đăng ký này lập tức bị loại khỏi số liệu cộng dồn cỗ bàn.
   - Nếu `approved = false`:
     - Bắt buộc phải có `rejectionReason` (lý do từ chối, ví dụ: "Ban tổ chức đã chốt đặt cọc và mua nguyên liệu").
     - Giữ nguyên số liệu cũ, chuyển trạng thái về `Confirmed` kèm ghi chú lý do từ chối.
   - Phát hành `RegistrationReviewedDomainEvent`.

5. **AC-05 (Bảo vệ khi sự kiện đã đóng):**
   - Khi sự kiện chuyển sang `Closed`, toàn bộ thao tác sửa đổi, hủy hoặc xét duyệt đăng ký đều bị từ chối (`BusinessRuleException`).

---

## 3. Kiến Trúc Dữ Liệu & Database Schema

### Flyway Migration: `V10__add_indexes_and_constraints_for_family_registrations.sql`

```sql
-- Chỉ mục hỗ trợ tính toán cộng dồn theo thời gian thực
CREATE INDEX IF NOT EXISTS idx_family_reg_event
    ON family_registrations(event_id, status);

CREATE INDEX IF NOT EXISTS idx_family_reg_representative
    ON family_registrations(representative_member_id);

-- Ràng buộc bất biến khẩu phần ăn
ALTER TABLE family_registrations
    DROP CONSTRAINT IF EXISTS chk_family_reg_meals;

ALTER TABLE family_registrations
    ADD CONSTRAINT chk_family_reg_meals
    CHECK (vegetarian_meals + savory_meals <= total_attendees);
```

### Mô hình Entity JPA
- `FamilyRegistration`: Đại diện cho lượt đăng ký của hộ gia đình với các trường:
  - `registration_id` VARCHAR(20) PRIMARY KEY (phát sinh tự động qua `RegistrationIdGenerator`).
  - `event_id` VARCHAR(20) FOREIGN KEY.
  - `representative_member_id` VARCHAR(20).
  - `total_attendees` INT, `vegetarian_meals` INT, `savory_meals` INT.
  - `dietary_requirements` VARCHAR(500), `transportation_notes` VARCHAR(500).
  - `status` VARCHAR(30) (`Confirmed`, `PendingModification`, `PendingCancellation`, `Cancelled`).
  - Các trường đề xuất: `proposed_total_attendees`, `proposed_vegetarian_meals`, `proposed_savory_meals`, `modification_reason`.
  - Xét duyệt: `reviewed_by_account_id`, `reviewed_at`, `rejection_reason`.
- `RegistrationMemberDetail`: Danh sách chi tiết thành viên gia phả đi cùng (khóa chính phức hợp `RegistrationMemberDetailId`).

---

## 4. Ma Trận Phân Quyền (Security Matrix)

| Endpoint | Vai trò hệ thống | Vai trò ngữ cảnh (`@eventSecurity`) | Ghi chú |
| :--- | :--- | :--- | :--- |
| `POST .../registrations` | `ADMIN`, `CLAN_HEAD`, `BRANCH_HEAD`, `MEMBER` | Không bắt buộc | Thành viên đăng ký cho chính hộ gia đình; Quản lý/BTC có thể đăng ký hộ |
| `GET .../registrations/summary` | `ADMIN`, `CLAN_HEAD` | `HeadOfOrganizer`, `OrganizerMember`, `EventTreasurer` | Ban tổ chức và ban tài chính nắm bắt số lượng cỗ bàn |
| `GET .../registrations` | `ADMIN`, `CLAN_HEAD` | `HeadOfOrganizer`, `OrganizerMember` | Xem danh sách đầy đủ hộ gia đình |
| `GET .../registrations/me` | `ADMIN`, `CLAN_HEAD`, `BRANCH_HEAD`, `MEMBER` | Không bắt buộc | Tra cứu thông tin hộ gia đình mình |
| `POST .../request-modification` | `ADMIN`, `CLAN_HEAD`, `BRANCH_HEAD`, `MEMBER` | Service kiểm tra: chủ hộ hoặc BTC | Chuyển sang `PendingModification` |
| `POST .../request-cancellation` | `ADMIN`, `CLAN_HEAD`, `BRANCH_HEAD`, `MEMBER` | Service kiểm tra: chủ hộ hoặc BTC | Chuyển sang `PendingCancellation` |
| `POST .../review` | `ADMIN`, `CLAN_HEAD` | `HeadOfOrganizer` | Phê duyệt thay đổi/hủy cỗ bàn |

---

## 5. Danh Mục Kiểm Thử (Verification Suites)

1. **Unit Test Service:**
   - `RegistrationServiceImplTest` (20 test cases): Kiểm thử toàn bộ nghiệp vụ kiểm tra điều kiện đăng ký, chống trùng, kiểm tra cỗ, đề xuất sửa/hủy và phê duyệt.
   - `EventQueryServiceImplTest` (4 test cases): Kiểm thử truy vấn `getRegistrationSummary` liên module.
2. **Contextual Security Test:**
   - `RegistrationContextualSecurityTest` (10 test cases): Kiểm thử ma trận phân quyền tĩnh và ngữ cảnh sự kiện.
3. **WebMvc Controller Test:**
   - `EventRegistrationControllerTest` (13 test cases): Kiểm thử các route REST, validation payload, HTTP status codes.
4. **Spring Modulith Architecture Test:**
   - `RegistrationArchitectureTest`: Đảm bảo module `event` đóng gói hoàn toàn package `internal`, giao tiếp liên module qua public `.api`.
5. **End-to-End Workflow Integration Test:**
   - `RegistrationWorkflowIntegrationTest`: Mô phỏng toàn bộ vòng đời đăng ký thực tế từ khởi tạo đến sửa đổi, hủy, duyệt và khóa sổ sự kiện.

Lệnh chạy kiểm thử xác minh toàn diện:
```bash
.\gradlew.bat test --tests "com.familyfund.event.*" --tests "com.familyfund.ArchitectureTest"
```

