# Đặc Tả Kỹ Thuật: US-03 - Chỉ Định & Thu Hồi Vai Trò Trưởng Nhánh, BTC Sự Kiện, Thủ Quỹ

## 1. Mục Tiêu & Yêu Cầu Nghiệp Vụ

### 1.1. Bối Cảnh & Mục Tiêu
Hệ thống Quản lý Dòng Họ (Family Management) vận hành theo mô hình phân quyền kép (**Dual-RBAC**):
1. **Phân quyền Tĩnh Toàn cục / Theo Nhánh (Static Hierarchy RBAC)**:
   - Vai trò **Trưởng Chi / Nhánh (`ROLE_BRANCH_HEAD`)**: Quản lý hồ sơ con cháu, duyệt đơn gia nhập và phát sinh mã thư mời cho chi nhánh mình phụ trách.
   - Bổ nhiệm hoặc thu hồi vai trò Trưởng nhánh cần đồng bộ 2 bảng dữ liệu:
     - Bảng `family_branches`: Gắn hoặc gỡ `representative_id` trỏ về `members.member_id`.
     - Bảng `account_roles`: Gắn hoặc gỡ `ROLE_BRANCH_HEAD` với tài khoản `user_accounts.account_id`.
2. **Phân quyền Động Theo Ngữ Cảnh Sự Kiện (Contextual Event RBAC)**:
   - Các vai trò gắn riêng với từng sự kiện:
     - **Trưởng Ban Tổ Chức (`HeadOfOrganizer`)**: Toàn quyền điều phối công việc, duyệt chi tiêu và điều chỉnh lịch trình sự kiện.
     - **Thành viên Ban Tổ Chức (`OrganizerMember`)**: Được phân công và cập nhật tiến độ công việc chuẩn bị lễ tế.
     - **Thủ Quỹ Sự Kiện (`EventTreasurer`)**: Quản lý thu/chi, phê duyệt các khoản đóng góp và tạm ứng ngân sách.
   - Lưu trữ tại bảng `event_personnel` với composite primary key `(event_id, member_id, event_role)`.
3. **Lưu Vết Kiểm Toán (`AUDIT_LOG`) & Thông Báo Tự Động**:
   - Mọi thao tác cấp/thu hồi quyền đều phải được ghi nhận vào bảng `audit_logs` (ai cấp, cho ai, lúc nào, vai trò gì, phạm vi nào).
   - Tự động phát thông báo tới người được chỉ định/thu hồi thông qua dịch vụ `NotificationSender` bất đồng bộ (`@Async`).

---

## 2. Quy Tắc Nghiệp Vụ Cốt Lõi

1. **Phân quyền thực thi**:
   - Chỉ **Trưởng tộc (`ROLE_CLAN_HEAD`)** hoặc **Quản trị viên (`ROLE_ADMIN`)** của dòng họ hiện tại mới có quyền gọi các API cấp/thu hồi vai trò.
2. **Điều kiện tài khoản thành viên**:
   - **Đối với Trưởng nhánh (`ROLE_BRANCH_HEAD`)**: Thành viên được chọn phải còn sống (`is_alive = true`), thuộc cùng dòng họ, và **bắt buộc đã có tài khoản `user_accounts` đang ở trạng thái `Active`**.
   - **Đối với BTC / Thủ quỹ sự kiện**: Thành viên phải còn sống và thuộc dòng họ. Nếu thành viên đã có tài khoản thì hệ thống gửi thông báo trực tiếp đến tài khoản đó.
3. **Quy tắc thay thế & thu hồi Trưởng nhánh**:
   - **Khi bổ nhiệm (`ASSIGN`)**: Nếu chi nhánh đang có Trưởng nhánh cũ, hệ thống tự động gán Trưởng nhánh mới vào `family_branches.representative_id`. Nếu Trưởng nhánh cũ không còn là đại diện của bất kỳ nhánh nào khác trong dòng họ, hệ thống tự động thu hồi `ROLE_BRANCH_HEAD` khỏi `account_roles` của người cũ.
   - **Khi thu hồi (`REVOKE`)**: Đặt `family_branches.representative_id = NULL`. Nếu thành viên bị thu hồi không còn đại diện cho nhánh nào khác trong họ, xóa `ROLE_BRANCH_HEAD` khỏi `account_roles`.
4. **Quy tắc Sự kiện Đã Đóng Sổ (Closed)**:
   - Tuân thủ Quy tắc B: Sự kiện ở trạng thái `Closed` sẽ bị khóa sổ hoàn toàn, không cho phép bổ nhiệm hoặc thu hồi nhân sự (`event_personnel`).
5. **Nhật ký kiểm toán (`audit_logs`)**:
   - Bảng `audit_logs` ghi nhận đầy đủ `audit_id`, `clan_id`, `actor_account_id`, `target_member_id`, `target_account_id`, `action_type` (`ASSIGN` / `REVOKE`), `role_code`, `scope_type` (`BRANCH` / `EVENT`), `scope_id`, `scope_name`, `description`, `created_at`.

---

## 3. Kiến Trúc & Thiết Kế API

### 3.1. Ranh Giới Spring Modulith
- **Module `genealogy`**: Cung cấp `GenealogyQueryService` (tìm branch theo clan, kiểm tra đại diện nhánh, lấy danh sách thành viên) và `GenealogyMemberCommandService` (gán/gỡ đại diện nhánh).
- **Module `identity`**: Cung cấp `IdentityRoleCommandService` (tra cứu account theo member, gán/gỡ role trong `account_roles`).
- **Module `event`**: Đặt `RoleManagementController`, `RoleManagementService`, entity `AuditLog`, `AuditLogRepository`, và `EventNotificationListener`.

### 3.2. Danh Sách RESTful API

| Method | Đường dẫn API | Phân quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/roles/assign` | `ROLE_CLAN_HEAD`, `ROLE_ADMIN` | Chỉ định vai trò Trưởng nhánh hoặc nhân sự sự kiện |
| `POST` | `/api/v1/roles/revoke` | `ROLE_CLAN_HEAD`, `ROLE_ADMIN` | Thu hồi vai trò Trưởng nhánh hoặc nhân sự sự kiện |
| `GET` | `/api/v1/roles/audit-logs` | `ROLE_CLAN_HEAD`, `ROLE_ADMIN` | Tra cứu lịch sử kiểm toán phân quyền |
| `GET` | `/api/v1/roles/overview` | `ROLE_CLAN_HEAD`, `ROLE_ADMIN` | Lấy dữ liệu tổng hợp nhánh & thành viên phục vụ UI |

---

## 4. Kiểm Thử Tự Động (Automated Testing)

1. `ArchitectureTest`: Kiểm tra tuân thủ tuyệt đối ranh giới module Spring Modulith.
2. `RoleManagementServiceImplTest`: Kiểm thử toàn bộ các ca nghiệp vụ:
   - Cấp quyền Trưởng nhánh thành công.
   - Thay thế Trưởng nhánh cũ và tự động gỡ `ROLE_BRANCH_HEAD` của người cũ khi không còn nhánh nào khác.
   - Chặn cấp quyền Trưởng nhánh nếu chưa có tài khoản Active.
   - Cấp/thu hồi nhân sự sự kiện (`HeadOfOrganizer`, `OrganizerMember`, `EventTreasurer`).
   - Chặn sửa nhân sự khi sự kiện đã `Closed`.
   - Chặn người gọi không có thẩm quyền (`AccessDeniedException`).
3. `RoleManagementControllerTest`: Kiểm thử HTTP request/response MockMvc cho toàn bộ 4 endpoints.
4. `IdentityRoleCommandServiceImplTest`: Kiểm thử mutation và query tài khoản.
5. `EventNotificationListenerTest`: Kiểm thử bắt domain event và gửi thông báo bất đồng bộ.
