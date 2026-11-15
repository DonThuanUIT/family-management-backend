# Đặc Tả Kỹ Thuật & Hướng Dẫn Vận Hành US-41: Sửa Hoặc Hủy Đăng Ký Tham Dự Trước Khi Ban Tổ Chức Chốt

Trạng thái: **Đã hoàn thành (Backend & Frontend)**. Ngày cập nhật: **12/11/2026**.

---

## 1. Tổng Quan Nghiệp Vụ (User Story & Acceptance Criteria)

### 1.1. Tóm tắt User Story
* **Mã User Story:** `US-41`
* **Nhóm chức năng:** Sự kiện & Đăng ký tham dự (`com.familyfund.event`)
* **Đối tượng sử dụng (Actor):** Đại diện hộ gia đình (`ROLE_MEMBER`, `ROLE_BRANCH_HEAD`, `ROLE_CLAN_HEAD`, `ROLE_ADMIN`).
* **Mong muốn (Goal):** Trực tiếp chỉnh sửa số lượng người tham dự, khẩu phần ăn (chay/mặn), phương tiện hoặc hủy lượt đăng ký của hộ gia đình khi sự kiện đang mở đăng ký (`RegistrationOpen`).
* **Lý do / Giá trị (Value):** Giúp hộ gia đình chủ động cập nhật thông tin trước hạn chốt danh sách mà không cần chờ xét duyệt thủ công, đồng thời bảo vệ số liệu đặt cỗ/khánh tiết ngay khi Ban tổ chức đã chốt danh sách (`RegistrationClosed`).

### 1.2. Tiêu chí chấp nhận (Acceptance Criteria - AC)

1. **AC-01 — Cập nhật đăng ký (`PUT /api/registrations/{id}` & `/api/v1/registrations/{id}`):**
   * Chỉ đại diện hộ gia đình sở hữu lượt đăng ký (`representative_member_id == currentMemberId`) hoặc nhân sự Ban tổ chức có quyền (`CLAN_HEAD`, `ADMIN`, `HeadOfOrganizer`) mới được phép cập nhật.
   * Chỉ cho phép cập nhật khi sự kiện ở trạng thái `RegistrationOpen` và lượt đăng ký chưa bị hủy (`status != Cancelled`).
   * Kiểm tra ràng buộc: `totalAttendees >= 1`, `vegetarianMeals >= 0`, `savoryMeals >= 0`, `vegetarianMeals + savoryMeals <= totalAttendees`.
   * Cập nhật trực tiếp số liệu và trả về `200 OK` kèm `RegistrationResponse`.

2. **AC-02 — Hủy đăng ký (`DELETE /api/registrations/{id}` & `/api/v1/registrations/{id}`):**
   * Chỉ đại diện hộ gia đình sở hữu lượt đăng ký hoặc nhân sự Ban tổ chức có quyền mới được phép hủy.
   * Chỉ cho phép hủy khi sự kiện ở trạng thái `RegistrationOpen` và lượt đăng ký chưa ở trạng thái `Cancelled`.
   * Chuyển trạng thái lượt đăng ký sang `Cancelled` và loại khỏi số liệu cộng dồn cỗ bàn.

3. **AC-03 — Chặn thao tác khi Ban tổ chức đã chốt danh sách (`RegistrationClosed`):**
   * Khi sự kiện chuyển sang `RegistrationClosed` (hoặc `Ongoing`, `Ended`, `Closed`), cả `PUT` và `DELETE` đều bị từ chối (`400 Bad Request` / `BusinessRuleException`) với thông báo rõ ràng yêu cầu **liên hệ trực tiếp Ban tổ chức (BTC)** để được hỗ trợ.

4. **AC-04 — Giao diện Frontend (Flutter):**
   * Trên chi tiết sự kiện (`EventDetailDialog`), hiển thị thông tin đăng ký hiện tại của hộ gia đình kèm nút **"Chỉnh sửa đăng ký"** và **"Hủy đăng ký"**.
   * Khi sự kiện đã chốt (`RegistrationClosed` hoặc các trạng thái sau khi chốt), **tự động khóa (disable) cả 2 nút** và hiển thị thông báo nổi bật hướng dẫn **liên hệ trực tiếp Ban tổ chức (BTC)**.

---

## 2. Hợp Đồng API (REST API Contract)

* `PUT /api/v1/registrations/{id}` (alias `PUT /api/registrations/{id}`)
  * Body: `{ "totalAttendees": 4, "vegetarianMeals": 1, "savoryMeals": 3, "dietaryRequirements": "...", "transportationNotes": "..." }`
  * `200 OK`: `ApiResponse<RegistrationResponse>`
  * `400 Bad Request` (khi `RegistrationClosed`): `"Danh sách tham dự đã được Ban tổ chức chốt. Vui lòng liên hệ trực tiếp Ban tổ chức sự kiện để được hỗ trợ."`
* `DELETE /api/v1/registrations/{id}` (alias `DELETE /api/registrations/{id}`)
  * `200 OK`: `ApiResponse<RegistrationResponse>` (`status = Cancelled`)
  * `400 Bad Request` (khi `RegistrationClosed`): Thông báo yêu cầu liên hệ trực tiếp Ban tổ chức.
