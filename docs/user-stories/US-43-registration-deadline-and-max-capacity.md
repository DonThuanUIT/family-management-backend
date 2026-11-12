# US-43: Cấu Hình Hạn Chót Đăng Ký, Sức Chứa Tối Đa & Bộ Đếm Ngược / Số Chỗ Còn Lại

> **Bounded Context:** `com.familyfund.event`
> **Trạng thái:** ✅ Đã hoàn thành (Backend & Frontend)

---

## 1. Bối Cảnh & Mục Tiêu Nghiệp Vụ

Ban Tổ Chức (BTC) cần kiểm soát thời hạn nhận đăng ký và giới hạn sức chứa tối đa của địa điểm tổ chức sự kiện. Thành viên khi truy cập trang đăng ký sự kiện sẽ nhìn thấy **bộ đếm ngược thời gian** đến hạn chót đăng ký và **số chỗ còn lại** theo thời gian thực để chủ động đăng ký trước khi hết hạn hoặc hết chỗ.

### Yêu cầu kỹ thuật
- **Backend:**
  - Bổ sung 2 trường cấu hình tùy chọn trên `Event`: `registrationDeadline` (`TIMESTAMPTZ`, hạn chót đăng ký) và `maxCapacity` (`INTEGER`, sức chứa tối đa, tối thiểu 1 nếu có nhập).
  - Cập nhật `POST /api/v1/events` (`CreateEventRequest`) và `PUT /api/v1/events/{eventId}` (`UpdateEventRequest`) cho phép BTC cấu hình/chỉnh sửa `registrationDeadline` và `maxCapacity`.
  - Cập nhật `EventResponse` & `EventDetailResponse` trả về `registrationDeadline`, `maxCapacity`, `currentAttendees` (tổng số người đã đăng ký chưa hủy) và `remainingSlots` (`max(0, maxCapacity - currentAttendees)`, hoặc `null` nếu không giới hạn).
  - Ràng buộc nghiệp vụ tại `RegistrationServiceImpl`:
    - Chặn đăng ký mới (`registerAttendees`) hoặc sửa đăng ký (`updateRegistration`) nếu đã quá `registrationDeadline`.
    - Chặn đăng ký mới nếu `currentAttendees + totalAttendees > maxCapacity`, hoặc sửa tăng số lượng vượt quá `maxCapacity`.
- **Frontend:**
  - **BTC (`CreateEventSheet`, `EditEventSheet`):** Bổ sung ô cấu hình **Hạn chót đăng ký** (chọn ngày & giờ) và **Sức chứa tối đa** (số chỗ tối đa, bỏ trống nếu không giới hạn).
  - **Thành viên (`EventRegistrationSection`):** Hiển thị **bộ đếm ngược thời gian** (từng giây theo thời gian thực) và **số chỗ còn lại** (`Còn X / Y chỗ`), tự động khóa nút đăng ký/chỉnh sửa kèm thông báo rõ ràng khi hết hạn hoặc hết chỗ.

---

## 2. Tiêu Chí Chấp Nhận (Acceptance Criteria)

| ID | Kịch bản | Kết quả mong đợi |
| :--- | :--- | :--- |
| **AC-01** | BTC tạo/cập nhật sự kiện có `registrationDeadline` và `maxCapacity` | Lưu thành công vào DB và trả về kèm `currentAttendees`, `remainingSlots`. |
| **AC-02** | Thành viên đăng ký hoặc sửa đăng ký sau thời điểm `registrationDeadline` | Backend từ chối (`400`/`422`) với thông báo đã hết hạn đăng ký. |
| **AC-03** | Thành viên đăng ký số lượng vượt quá số chỗ còn lại (`remainingSlots`) | Backend từ chối với thông báo sự kiện không đủ chỗ trống. |
| **AC-04** | Trên UI thành viên, sự kiện có hạn chót trong tương lai và giới hạn sức chứa | Hiển thị bộ đếm ngược thời gian trực tiếp và số chỗ còn lại (`Còn X / Y chỗ`). |
| **AC-05** | Khi hết hạn đăng ký hoặc `remainingSlots == 0` | Nút đăng ký bị khóa tự động kèm banner thông báo hết hạn / hết chỗ. |
