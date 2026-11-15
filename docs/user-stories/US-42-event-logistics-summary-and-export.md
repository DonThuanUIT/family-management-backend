# US-42: Bảng Tổng Hợp Tức Thời Cho BTC (Số Cá Nhân/Gia Đình, Tổng Suất Ăn, Phương Tiện & Xuất Danh Sách)

> **Bounded Context:** `com.familyfund.event` (tích hợp `com.familyfund.genealogy.api.GenealogyQueryService`)
> **Trạng thái:** ✅ Đã hoàn thành (Backend & Frontend)

---

## 1. Bối Cảnh & Mục Tiêu Nghiệp Vụ

Ban Tổ Chức (BTC) sự kiện dòng họ (Hội trưởng, Thành viên BTC, Thủ quỹ sự kiện, Quản trị viên) cần theo dõi số liệu hậu cần theo thời gian thực để chuẩn bị mâm cỗ (cỗ mặn / cỗ chay), bố trí xe đưa đón và in danh sách điểm danh theo từng chi/nhánh gia đình hoặc toàn dòng họ.

### Yêu cầu kỹ thuật
- **Backend:**
  - `GET /api/events/{id}/registration-summary` và `GET /api/v1/events/{id}/registration-summary` (hỗ trợ query param tùy chọn `branchId`):
    - Tính toán thời gian thực từ các đăng ký chưa bị hủy (`status != Cancelled`):
      - Tổng số hộ gia đình (`totalRegistrations`)
      - Tổng số cá nhân (`totalAttendees`)
      - Tổng suất cỗ mặn (`totalSavoryMeals`)
      - Tổng suất cỗ chay (`totalVegetarianMeals`)
      - Tổng số chỗ ngồi xe đưa đón (`totalShuttleSeats`)
    - Hỗ trợ lọc theo chi/nhánh gia đình (`branchId`) thông qua `GenealogyQueryService`.
    - Trả về danh sách chi tiết từng hộ đăng ký (`registrations`) và danh sách chi/nhánh khả dụng (`availableBranches`) phục vụ hiển thị Dashboard.
  - `GET /api/events/{id}/registration-summary/export` và `GET /api/v1/events/{id}/registration-summary/export` (hỗ trợ `branchId`, `format=xlsx|csv`):
    - Xuất file Excel (`.xlsx` dùng Apache POI) hoặc `.csv` (UTF-8 BOM) danh sách điểm danh & chia cỗ cho BTC.
- **Frontend:**
  - Màn hình **Dashboard Hậu Cần Sự Kiện** (`/events/:eventId/logistics`) cho BTC hiển thị:
    - Các thẻ số liệu KPI tức thời (Hộ gia đình, Cá nhân, Cỗ mặn, Cỗ chay, Chỗ ngồi xe đưa đón).
    - Bộ lọc theo chi/nhánh gia đình.
    - Bảng chi tiết đăng ký từng hộ theo bộ lọc hiện tại.
    - Nút **Xuất danh sách điểm danh / chia cỗ** (tải file `.xlsx` hoặc `.csv`).

---

## 2. Tiêu Chí Chấp Nhận (Acceptance Criteria)

| ID | Kịch bản | Kết quả mong đợi |
| :--- | :--- | :--- |
| **AC-01** | BTC gọi `GET /api/events/{id}/registration-summary` không truyền `branchId` | Trả về `200 OK` với tổng số hộ, tổng cá nhân, tổng cỗ mặn, tổng cỗ chay, tổng chỗ ngồi xe đưa đón của toàn sự kiện (loại trừ `Cancelled`). |
| **AC-02** | BTC lọc theo `branchId` hợp lệ | Chỉ tính tổng và liệt kê các hộ có đại diện thuộc chi/nhánh `branchId`. |
| **AC-03** | Truyền `branchId` không tồn tại trong dòng họ hiện tại | Trả về `404 Not Found`. |
| **AC-04** | Thành viên thường không có quyền BTC/ClanHead/Admin gọi API tổng hợp hoặc xuất file | Trả về `403 Forbidden`. |
| **AC-05** | BTC gọi `GET /api/events/{id}/registration-summary/export` | Trả về file `.xlsx` (hoặc `.csv`) với header `Content-Disposition: attachment` chứa bảng tổng hợp KPI và danh sách điểm danh/chia cỗ theo bộ lọc `branchId`. |
| **AC-06** | Trên Flutter UI, BTC mở Dashboard Hậu Cần từ chi tiết sự kiện | Hiển thị 5 thẻ số liệu, dropdown lọc chi nhánh (tải lại số liệu tức thời khi đổi nhánh), bảng chi tiết hộ đăng ký và nút Xuất danh sách điểm danh/chia cỗ. |

---

## 3. Thiết Kế Kỹ Thuật

### 3.1. Cơ sở dữ liệu & Entity
- Migration `V21__add_shuttle_seats_to_family_registrations.sql`: Bổ sung cột `shuttle_seats INTEGER NOT NULL DEFAULT 0` vào bảng `family_registrations`.
- `FamilyRegistration` bổ sung trường `shuttleSeats`. Khi `shuttleSeats == null || shuttleSeats == 0`, hệ thống fallback trích xuất số chỗ từ `transportationNotes` nếu ghi chú có dạng số chỗ (ví dụ `"2 chỗ"`, `"xe 4 chỗ"`).

### 3.2. Thành phần Backend (`com.familyfund.event`)
- `EventLogisticsController`:
  - `GET /api/v1/events/{eventId}/registration-summary` & `GET /api/events/{eventId}/registration-summary`
  - `GET /api/v1/events/{eventId}/registration-summary/export` & `GET /api/events/{eventId}/registration-summary/export`
- `RegistrationService` / `RegistrationServiceImpl`:
  - `getEventLogisticsSummary(String eventId, String branchId)`
- `EventLogisticsExportService`:
  - `exportLogisticsReport(EventLogisticsSummaryResponse summary, String format)`

### 3.3. Thành phần Frontend (`lib/features/event`)
- `EventLogisticsSummaryModel`, `HouseholdLogisticsItemModel`, `LogisticsBranchOptionModel` trong `event_models.dart`.
- `EventRemoteDataSource`, `EventRepository`, `EventRepositoryImpl`: `getEventRegistrationSummary`, `exportEventRegistrationSummary`.
- `EventLogisticsDashboardScreen` + route `/events/:eventId/logistics` + nút mở từ `EventDetailDialog`.

---

## 4. Kiểm Thử Tự Động

### Backend
```powershell
.\gradlew.bat test --tests "com.familyfund.event.internal.controller.EventLogisticsControllerTest" --tests "com.familyfund.event.internal.service.RegistrationServiceUs42Test" --tests "com.familyfund.ArchitectureTest"
```

### Frontend
```powershell
flutter analyze
flutter test test/features/event/event_logistics_dashboard_screen_test.dart
```
