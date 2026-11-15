# Đặc Tả Kỹ Thuật & Hướng Dẫn Vận Hành US-27: Ghi Nhận Tình Trạng Sống/Đã Mất, Ngày Mất & Tính Ngày Giỗ Âm Lịch

Trạng thái: **Đã hoàn thành Backend (Sẵn sàng triển khai Frontend)**. Ngày cập nhật: **11/11/2026**.

---

## 1. Nguồn Yêu Cầu & Tổng Quan Nghiệp Vụ

Nguồn chuẩn: [`UserStory_HeThongQuanLySuKienVaQuyDaiGiaDinh.xlsx`](../../../UserStory_HeThongQuanLySuKienVaQuyDaiGiaDinh.xlsx), sheet **User Story**, hàng **13** (`A13:L13`).

### 1.1. Tóm tắt User Story
* **Mã User Story:** `US-27`
* **Nhóm chức năng:** Cây gia phả & thành viên
* **Đối tượng sử dụng (Actor):** Trưởng nhánh (`ROLE_BRANCH_HEAD`), Trưởng tộc (`ROLE_CLAN_HEAD`), Quản trị viên (`ROLE_ADMIN`).
* **Mong muốn (Goal):** Ghi nhận tình trạng thành viên (còn sống / đã mất), ngày mất (Dương lịch) và tự động tính ngày giỗ (Âm lịch).
* **Lý do / Giá trị (Value):** Dòng họ nắm rõ ai còn sống, ai đã khuất; lưu giữ chính xác ngày giỗ theo lịch âm truyền thống để thờ phụng và phục vụ việc tự động nhắc nhở tổ chức đám giỗ dòng họ hàng năm (US-40).
* **Mức độ ưu tiên:** Bắt buộc (Must-Have); Đợt 1 – Nền tảng.
* **Given (Bối cảnh):** Thành viên đã có hồ sơ trên hệ thống dòng họ.
* **When (Hành động):** Người quản lý (Trưởng tộc / Trưởng nhánh) đổi tình trạng sang "Đã mất" (`Deceased`) và nhập ngày mất (Dương lịch).
* **Then (Kết quả):** Hệ thống cập nhật trạng thái `Deceased`, tự động chuyển đổi ngày mất Dương lịch sang ngày giỗ Âm lịch (`death_date_lunar`), khóa tài khoản đăng nhập (nếu có), đồng thời **giữ nguyên 100% vị trí nút trên cây phả hệ và toàn bộ lịch sử đóng góp tài chính**.

---

## 2. Tiêu Chí Chấp Nhận (Acceptance Criteria - AC)

### AC-01 — Cập nhật trạng thái thành viên sang "Đã mất" (Deceased)
* **Endpoint:** `PUT /api/v1/members/{memberId}/life-status`
* **Quyền hạn (Dual-RBAC & Branch Scoping):**
  - `CLAN_HEAD` và `ADMIN`: Có quyền cập nhật cho mọi thành viên thuộc dòng họ (`clanId`).
  - `BRANCH_HEAD`: Chỉ có quyền cập nhật cho các thành viên thuộc chi nhánh mà mình làm đại diện/quản lý (`branchId`).
  - `MEMBER`: Bị từ chối `403 Forbidden`.
* **Request Body:**
  ```json
  {
    "status": "Deceased",
    "deathDate": "2024-03-24",
    "burialPlace": "Nghĩa trang quê nhà, xã Đông Kết, huyện Khoái Châu, Hưng Yên"
  }
  ```
* **Validation & Business Invariants:**
  1. `status`: Bắt buộc, giá trị hợp lệ: `"Living"` hoặc `"Deceased"`.
  2. `deathDate`:
     - Bắt buộc khi `status == "Deceased"`.
     - Phải là ngày trong quá khứ hoặc hiện tại (`deathDate <= LocalDate.now()`). Không được chọn ngày trong tương lai (`400 Bad Request`).
     - Nếu thành viên đã có ngày sinh (`dateOfBirth`), ngày mất phải sau hoặc bằng ngày sinh (`deathDate >= dateOfBirth`), ngược lại trả về `400 Bad Request`.
  3. `burialPlace`: Tùy chọn, tối đa 255 ký tự.

### AC-02 — Tự động chuyển đổi Dương lịch <-> Âm lịch (Vietnamese Lunar Conversion)
* Hệ thống tích hợp thuật toán thiên văn chuyển đổi Âm lịch Việt Nam chuẩn múi giờ GMT+7 (thuật toán Hồ Ngọc Đức):
  - Chuyển đổi `deathDate` (Dương lịch) thành: Ngày âm, Tháng âm, Năm âm, Tháng nhuận (`isLeapMonth`), và Can Chi của năm (ví dụ: Giáp Thìn, Ất Tỵ...).
  - Tự động sinh chuỗi `death_date_lunar` chuẩn mực và trang trọng:
    - Định dạng chuẩn: `Ngày dd tháng MM năm [Can Chi] (Âm lịch)` hoặc ngắn gọn `dd/MM [Can Chi]`.
    - Ví dụ: `2024-03-24` $\rightarrow$ `15/02 Giáp Thìn` (Ngày 15 tháng 2 năm Giáp Thìn).
* Dữ liệu này được lưu trực tiếp vào cột `death_date_lunar` và `death_date` trong CSDL để phục vụ hiển thị và làm mốc tìm kiếm cho sự kiện giỗ chạp (US-40).

### AC-03 — Đổi ngược trạng thái từ "Đã mất" về "Còn sống" (Hiệu đính sai sót)
* Trong trường hợp người quản lý bấm nhầm hoặc cần hiệu đính sai sót:
  - Cho phép gửi `status = "Living"`.
  - Hệ thống tự động xóa `deathDate = null`, `deathDateLunar = null`, `burialPlace = null`.
  - Trạng thái thành viên trở về `Living`.

### AC-04 — Tự động khóa tài khoản người dùng liên kết (Account Lockout)
* Nếu thành viên qua đời đã có tài khoản người dùng (`UserAccount` liên kết qua `member_id`):
  - Hệ thống tự động chuyển `account_status` của tài khoản sang `Deactivated` (hoặc `Suspended`).
  - Chặn đăng nhập và vô hiệu hóa JWT refresh token của tài khoản này ngay lập tức.
  - Khi người dùng này cố gắng đăng nhập, hệ thống từ chối và báo rõ: *"Tài khoản đã ngừng hoạt động do thành viên đã qua đời."*
* **Cơ chế Module Decoupling (Spring Modulith):**
  - Module `genealogy` phát ra sự kiện Domain Event: `MemberLifeStatusChangedEvent`.
  - Module `identity` lắng nghe sự kiện (`@ApplicationModuleListener`) và thực hiện khóa tài khoản một cách an toàn, không vi phạm phụ thuộc vòng.

### AC-05 — Bảo tồn toàn vẹn cây gia phả và sổ cái đóng góp (Data Preservation)
* **Cây gia phả:** Nút thành viên vẫn giữ nguyên 100% tại vị trí cũ trong cây gia phả (`parent_id`, `branch_id`, các liên kết phu thê, con cái không bị xóa hay thay đổi).
* **Lịch sử đóng góp quỹ dòng họ:** Toàn bộ lịch sử đóng góp bằng tiền mặt, chuyển khoản (`contributions`), biên nhận điện tử (`e-receipts`), và hiện vật (`clan_assets`) liên quan đến `member_id` này vẫn được giữ nguyên vẹn trong sổ cái tài chính của dòng họ.

### AC-06 — Giao diện hiển thị trang trọng (Frontend UI/UX)
* **Giao diện thẻ thành viên đã mất:**
  - Viền và huy hiệu trang trọng: Viền màu ngà trầm/vàng đồng cổ (`#B8860B` / `#54433E`), biểu tượng hoa sen 🪷 hoặc nến thơm 🕯️.
  - Huy hiệu trạng thái: Nhãn `"Đã mất"` kèm dòng chữ trang trọng: `"Giỗ: 15/02 ÂL (Giáp Thìn)"`.
  - Tuổi thọ: Tự động tính `"Hưởng thọ: X tuổi"`.
* **Bộ chọn ngày mất (Date Picker & Lunar Live Preview):**
  - Cho phép người quản lý chọn ngày mất Dương lịch.
  - Tự động hiển thị ngay xem trước (Live Preview) ngày giỗ Âm lịch tương ứng bên dưới mà không cần chờ tải lại trang.
  - Cảnh báo rõ việc khóa tài khoản và cam kết bảo lưu phả hệ.

---

## 3. Thiết Kế Cơ Sở Dữ Liệu (Database Migration)

### Kịch bản Migration Flyway: `V18__add_death_date_and_burial_place_to_members.sql`

```sql
-- V18: Bổ sung ngày mất dương lịch và địa điểm an táng cho thành viên (US-27)
ALTER TABLE members
    ADD COLUMN IF NOT EXISTS death_date DATE,
    ADD COLUMN IF NOT EXISTS burial_place VARCHAR(255);

-- Ràng buộc kiểm tra tính hợp lý của ngày mất và ngày sinh
ALTER TABLE members
    DROP CONSTRAINT IF EXISTS chk_members_death_date_after_birth;

ALTER TABLE members
    ADD CONSTRAINT chk_members_death_date_after_birth
    CHECK (death_date IS NULL OR date_of_birth IS NULL OR death_date >= date_of_birth);

COMMENT ON COLUMN members.death_date IS 'Ngày mất theo Dương lịch của thành viên (US-27)';
COMMENT ON COLUMN members.burial_place IS 'Nơi an táng, phần mộ hoặc nghĩa trang yên nghỉ của thành viên (US-27)';
COMMENT ON COLUMN members.death_date_lunar IS 'Ngày giỗ theo Âm lịch (ví dụ: 15/02 Giáp Thìn) tự động tính toán (US-27)';
```

---

## 4. Thiết Kế Kiến Trúc & Spring Modulith

Tính năng thuộc bounded context: **`com.familyfund.genealogy`**, tương tác bất đồng bộ qua Domain Event với **`com.familyfund.identity`**.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CLIENT (Flutter Web / Mobile)                   │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │  PUT /api/v1/members/{memberId}/life-status
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ MODULE: com.familyfund.genealogy                                       │
│  ├─ MemberController                                                  │
│  │   └─ updateLifeStatus(memberId, UpdateLifeStatusRequest)           │
│  ├─ MemberService / MemberServiceImpl                                  │
│  │   ├─ authorizeBranchScope (Kiểm tra Dual-RBAC)                     │
│  │   ├─ VietnameseLunarCalendarConverter (Tính ngày giỗ Âm lịch)       │
│  │   ├─ MemberRepository.save(member)                                 │
│  │   └─ ApplicationEventPublisher.publishEvent(                        │
│  │        new MemberLifeStatusChangedEvent(memberId, status))          │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ Domain Event (In-memory / Modulith)
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│ MODULE: com.familyfund.identity                                        │
│  └─ MemberLifeStatusEventListener                                      │
│      └─ @ApplicationModuleListener                                     │
│         handleMemberLifeStatusChanged(event):                          │
│         Nếu status == 'Deceased' -> Khóa UserAccount liên kết         │
│         (account_status = 'Deactivated')                               │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Chi Tiết API Contracts

### 5.1. Cập nhật tình trạng Sống / Đã mất
* **Phương thức:** `PUT`
* **Đường dẫn:** `/api/v1/members/{memberId}/life-status`
* **Headers:**
  - `Authorization: Bearer <jwt-token>`
  - `Content-Type: application/json`

#### Request Payload (Báo tử - Deceased):
```json
{
  "status": "Deceased",
  "deathDate": "2024-03-24",
  "burialPlace": "Khu lăng mộ tổ họ Nguyễn, Đông Kết, Khoái Châu, Hưng Yên"
}
```

#### Request Payload (Chuyển về còn sống - Living):
```json
{
  "status": "Living",
  "deathDate": null,
  "burialPlace": null
}
```

#### Response Body (200 OK):
```json
{
  "success": true,
  "message": "Cập nhật tình trạng thành viên thành công",
  "data": {
    "memberId": "MEM_202611_0001",
    "fullName": "Nguyễn Văn An",
    "dateOfBirth": "1940-08-10",
    "gender": "Male",
    "generation": 3,
    "status": "Deceased",
    "deathDate": "2024-03-24",
    "deathDateLunar": "15/02 Giáp Thìn",
    "burialPlace": "Khu lăng mộ tổ họ Nguyễn, Đông Kết, Khoái Châu, Hưng Yên",
    "branchId": "BR_02",
    "branchName": "Chi 2 (Đông Kết)",
    "clanId": "CLAN_01",
    "isPlaceholder": false,
    "isAccountLocked": true
  }
}
```

#### Bảng Mã Lỗi (Error Responses):
| HTTP Status | Error Code | Lý do |
| :--- | :--- | :--- |
| `400 Bad Request` | `INVALID_STATUS` | `status` không phải là `Living` hoặc `Deceased`. |
| `400 Bad Request` | `DEATH_DATE_REQUIRED` | `status` là `Deceased` nhưng không truyền `deathDate`. |
| `400 Bad Request` | `FUTURE_DEATH_DATE` | `deathDate` ở tương lai (`deathDate > now`). |
| `400 Bad Request` | `DEATH_BEFORE_BIRTH` | `deathDate` trước ngày sinh (`dateOfBirth`). |
| `403 Forbidden` | `ACCESS_DENIED` | Trưởng nhánh cố cập nhật thành viên thuộc chi nhánh khác, hoặc Thành viên thường thực hiện thao tác. |
| `404 Not Found` | `MEMBER_NOT_FOUND` | Không tìm thấy thành viên với `memberId` tương ứng. |

---

## 6. Thuật Toán Chuyển Đổi Dương Lịch <-> Âm Lịch (Vietnamese Lunar Algorithm)

Thuật toán chuyển đổi ngày tháng Dương lịch sang Âm lịch dựa trên công trình thiên văn toán học chuẩn của **Hồ Ngọc Đức** (Viện Công nghệ Thông tin):
1. **Múi giờ:** GMT+7 (Việt Nam Standard Time, kinh độ $105^\circ\text{E}$).
2. **Tham số đầu vào:** Năm, Tháng, Ngày Dương lịch (`year`, `month`, `day`).
3. **Các bước tính toán:**
   - Tính ngày Julius ($JD$).
   - Xác định thời điểm Trăng Mới (Sóc - New Moon) gần nhất.
   - Tính tháng âm lịch và xác định tháng nhuận (Leap month) dựa trên Tiết Khí Mặt Trời (Solar Terms / 24 Tiết Khí).
   - Xác định Can Chi của năm âm lịch:
     - Can (10): Giáp, Ất, Bính, Đinh, Mậu, Kỷ, Canh, Tân, Nhâm, Quý.
     - Chi (12): Tý, Sửu, Dần, Mão, Thìn, Tỵ, Ngọ, Mùi, Thân, Dậu, Tuất, Hợi.
4. **Kết quả trả về:**
   - `lunarDay` (1..30)
   - `lunarMonth` (1..12)
   - `lunarYear` (VD: 2024)
   - `isLeap` (true/false)
   - `lunarYearName` (VD: "Giáp Thìn")
   - `formattedString`: `15/02 Giáp Thìn` (hoặc `Ngày 15 tháng 2 năm Giáp Thìn`).

---

## 7. Thiết Kế Giao Diện Người Dùng (Frontend UI/UX)

### 7.1. Thẻ thành viên trên Danh sách & Cây Gia Phả
* **Thành viên còn sống:** Giữ nguyên giao diện chuẩn, avatar màu xanh/đỏ theo giới tính, huy hiệu vai trò.
* **Thành viên đã mất:**
  - **Tông màu:** Viền xám trầm (`#54433E`) phối vàng đồng trang nghiêm (`#B8860B`).
  - **Huy hiệu thờ cúng:** Badge màu đồng trầm có icon hoa sen 🪷 hoặc nến hương: `"Đã mất"` kèm `"Giỗ: 15/02 ÂL (Giáp Thìn)"`.
  - **Tuổi thọ:** Hiển thị `"Hưởng thọ: 84 tuổi"` (tự động tính dựa vào năm sinh và năm mất).
  - **Nơi an táng:** Hiển thị địa điểm phần mộ nếu có.

### 7.2. Dialog Cập Nhật Tình Trạng Sinh/Tử (`UpdateLifeStatusDialog`)
* **Header:** Tiêu đề *"Cập Nhật Tình Trạng Thành Viên - [Tên Thành Viên]"*.
* **Radio Segmented Switch:**
  - `[🌿 Còn sống]` | `[🕯️ Đã mất]`
* **Khi chọn "Đã mất":**
  1. **Bộ chọn Ngày mất Dương lịch:**
     - DatePicker tiếng Việt, chặn chọn ngày tương lai hoặc ngày trước ngày sinh.
  2. **Khung hiển thị Ngày giỗ Âm lịch (Live Preview):**
     - Nền màu vàng ngà ấm (`#FFFDF8`), viền vàng đồng (`#E6DFD8`).
     - Tự động hiển thị ngay:
       - **Ngày giỗ Âm lịch:** `Ngày 15 tháng 02 (Âm lịch) - Năm Giáp Thìn`
       - **Tuổi hưởng thọ:** `84 tuổi`
  3. **Trường nhập Nơi an táng / Phần mộ (Tùy chọn):**
     - TextField nhập tên nghĩa trang, khu mộ gia tộc.
  4. **Hộp lưu ý chính sách dòng họ:**
     - Biểu tượng khiên bảo mật: *"Khi ghi nhận Đã mất: Tài khoản đăng nhập (nếu có) sẽ bị khóa. Vị trí trên cây phả hệ và toàn bộ lịch sử đóng góp quỹ dòng họ của thành viên được bảo lưu trọn vẹn 100%."*
* **Nút hành động:**
  - *"Hủy bỏ"*
  - *"Xác Nhận Lưu"* (Màu đỏ son `AppTheme.primaryCrimson`, hiển thị spinner khi đang gửi API).

---

## 8. Kế Hoạch Kiểm Thử (Test Plan)

### 8.1. Backend Tests
1. **Lunar Calendar Conversion Test (`VietnameseLunarCalendarConverterTest`):**
   - Kiểm tra ngày sóc, ngày rằm các năm bình thường và năm nhuận.
   - Kiểm tra Can Chi năm (2024: Giáp Thìn, 2025: Ất Tỵ, 2026: Bính Ngọ...).
2. **Service Unit Test (`MemberLifeStatusTest`):**
   - Đổi sang `Deceased`: Kiểm tra tính đúng ngày giỗ âm lịch, lưu vào database.
   - Đổi về `Living`: Kiểm tra xóa trắng `deathDate` và `deathDateLunar`.
   - Kiểm tra chặn ngày mất trong tương lai và ngày mất trước ngày sinh.
   - Kiểm tra phân quyền: Trưởng nhánh quản lý chi nhánh của mình $\rightarrow$ thành công; chi nhánh khác $\rightarrow$ ném `AccessDeniedException`.
3. **Modulith Event & Account Lockout Test (`MemberLifeStatusEventListenerTest`):**
   - Khi phát sự kiện `MemberLifeStatusChangedEvent(Deceased)` $\rightarrow$ `UserAccount` tương ứng bị chuyển sang `Deactivated`.
   - Đăng nhập thử bằng tài khoản bị khóa $\rightarrow$ bị chặn với thông báo lỗi thích hợp.
4. **Integration Test (`MemberLifeStatusControllerTest`):**
   - Test MockMvc gọi endpoint `PUT /api/v1/members/{memberId}/life-status`.

### 8.2. Frontend Tests
1. **Unit Test Bộ chuyển đổi Âm lịch Dart (`vietnamese_lunar_calendar_test.dart`):**
   - Kiểm tra chuyển đổi ngày Dương lịch sang ngày Âm lịch, Can Chi năm.
2. **Widget Test Dialog cập nhật sinh/tử (`update_life_status_dialog_test.dart`):**
   - Chọn ngày mất Dương lịch $\rightarrow$ Live preview hiển thị đúng ngày giỗ Âm lịch.
   - Kiểm tra thông báo lưu ý và nút xác nhận.
3. **Widget Test Hiển thị Thẻ Thành Viên Đã Mất (`deceased_member_card_test.dart`):**
   - Kiểm tra viền và nhãn thờ cúng trang trọng trên thẻ thành viên đã mất.
