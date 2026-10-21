# Các Luồng Nghiệp Vụ Cốt Lõi (Core Business Flows)

Tài liệu này mô tả chi tiết các luồng dữ liệu và quy trình nghiệp vụ xuyên suốt các phân hệ của hệ thống **Family Management Backend**.

---

## 1. Luồng 1: Xin Gia Nhập Dòng Họ & Định Danh Tài Khoản

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng mới
    participant API as JoinRequestController
    actor BranchHead as Trưởng Chi / Trưởng Tộc
    participant DB as PostgreSQL (clans, members, user_accounts)

    User->>API: Gửi đơn xin gia nhập (clan_id, họ tên, SĐT, chi đề xuất)
    API->>DB: Lưu bản ghi vào bảng join_requests (status = 'Pending')
    BranchHead->>API: Xem danh sách đơn chờ duyệt
    BranchHead->>API: Phê duyệt đơn (Approved)
    API->>DB: Tạo bản ghi mới trong bảng members (sinh mã claim_code)
    API->>DB: Cập nhật join_requests (status = 'Approved')
    User->>API: Nhập claim_code & mật khẩu để kích hoạt tài khoản
    API->>DB: Tạo user_accounts liên kết 1-1 với member_id
    API->>DB: Gán vai trò mặc định ROLE_MEMBER vào account_roles
```

### Các bước thực thi:
1. **Tiếp nhận yêu cầu**: Người dùng tải ứng dụng, tìm kiếm dòng họ qua tên hoặc mã mời (`invite_code`), gửi yêu cầu gia nhập kèm số điện thoại liên lạc.
2. **Xác minh & Phê chuẩn**: Trưởng họ hoặc Trưởng chi đối chiếu phả hệ thực tế và chấp thuận đơn.
3. **Liên kết hồ sơ (Claim Profile)**: Hệ thống sinh hồ sơ thành viên `members` với mã `claim_code`. Người dùng hoàn tất đăng ký tài khoản `user_accounts` gắn liền với hồ sơ phả hệ của mình.

---

## 2. Luồng 2: Quản Lý Sự Kiện & Phân Quyền Động (Contextual RBAC)

Khác với quyền toàn cục (Global Role), quyền hạn trong sự kiện được cấp phát linh hoạt và gắn chặt với trạng thái vòng đời sự kiện:

```mermaid
stateDiagram-v2
    [*] --> Draft: Trưởng ban tạo kế hoạch
    Draft --> RegistrationOpen: Mở cổng đăng ký hộ gia đình
    RegistrationOpen --> Ongoing: Bắt đầu diễn ra ngày giỗ / lễ hội
    Ongoing --> Closed: Khóa sổ & Kết chuyển quỹ

    note right of Ongoing
      Contextual RBAC:
      - HeadOfOrganizer: Điều hành
      - EventTreasurer: Thu chi, lập phiếu
      - OrganizerMember: Hỗ trợ
    end note

    note right of Closed
      Toàn bộ quyền EventPersonnel
      tự động chuyển sang CHẾ ĐỘ CHỈ ĐỌC (Read-Only)
    end note
```

- **Chỉ định nhân sự**: Bảng `event_personnel` lưu các vai trò `HeadOfOrganizer`, `OrganizerMember`, `EventTreasurer` cho từng sự kiện.
- **Tự động thu hồi quyền**: Khi sự kiện chuyển trạng thái sang `Closed`, hệ thống chặn toàn bộ thao tác thêm/sửa/xóa phiếu chi, phiếu thu, phiếu tạm ứng của sự kiện đó, bảo toàn tính toàn vẹn số liệu.

---

## 3. Luồng 3: Tạm Ứng, Quyết Toán & Cơ Chế Chặn Khóa Sổ

Quy trình quản lý chi tiêu nghiêm ngặt giúp ngăn chặn thất thoát quỹ và đảm bảo mọi khoản tạm ứng đều được đối soát:

```mermaid
sequenceDiagram
    autonumber
    actor Assignee as Người phụ trách việc
    actor Treasurer as Thủ quỹ sự kiện
    participant System as Hệ thống Backend
    participant DB as PostgreSQL

    Treasurer->>System: Lập phiếu tạm ứng advance_vouchers (task_id, số tiền > 0)
    Assignee->>Treasurer: Đi chợ / mua sắm & mang hóa đơn về quyết toán
    Treasurer->>System: Lập phiếu quyết toán expense_settlement_vouchers
    System->>System: Tính difference_amount = actual_expense_amount - advance_amount
    alt Chi phí hợp lệ
        Treasurer->>System: Đặt status = 'Valid'
        System->>DB: Lưu quyết toán hợp lệ
    else Hóa đơn thiếu minh bạch / chi vượt dự toán vô lý
        Treasurer->>System: Đặt status = 'NeedsExplanation' & ghi lý do
        System->>DB: Lưu bản ghi kèm cờ NeedsExplanation
        Note over System, DB: HỆ THỐNG KÍCH HOẠT CHẾ ĐỘ CHẶN KHÓA SỔ SỰ KIỆN
    end
```

### Cơ chế Chặn Khóa Sổ Sự Kiện (Business Invariant):
- Khi Trưởng ban tổ chức thực hiện lệnh đóng sự kiện (`PUT /events/{id}/close`), hệ thống thực thi truy vấn kiểm tra:
  ```sql
  SELECT COUNT(*) FROM expense_settlement_vouchers esv
  JOIN advance_vouchers av ON esv.advance_voucher_id = av.advance_voucher_id
  JOIN event_tasks et ON av.task_id = et.task_id
  WHERE et.event_id = :eventId AND esv.status = 'NeedsExplanation';
  ```
- Nếu kết quả $> 0$, API lập tức trả về mã lỗi `400 Bad Request` kèm thông báo: *"Sự kiện còn khoản chi chưa giải trình hợp lệ, không thể khóa sổ"*.

---

## 4. Luồng 4: Đóng Góp Quỹ Họ & Phân Bổ Công Đức Đệ Quy

Hệ thống hỗ trợ 2 hình thức đóng góp qua mô hình đa hình (Polymorphism / Class Table Inheritance):

```mermaid
graph TD
    Contrib["Bản ghi gốc: CONTRIBUTION"] -->|1-1 Subtype| Cash["CASH_CONTRIBUTION (Tiền mặt / CK)"]
    Contrib -->|1-1 Subtype| InKind["IN_KIND_CONTRIBUTION (Hiện vật)"]
    Contrib -->|1-N Phân bổ| Detail["CONTRIBUTION_MEMBER_DETAIL"]
    
    Detail -->|Thành viên 1| M1["MEMBER: Cha (recorded_amount)"]
    Detail -->|Thành viên 2| M2["MEMBER: Con trai (recorded_amount)"]
    Detail -->|Thành viên 3| M3["MEMBER: Con dâu (recorded_amount)"]
    
    InKind -->|Hướng xử lý: AddedToCommonAssets| ClanAsset["CLAN_ASSET (Danh mục tài sản chung)"]
```

### Thuật toán Tính Tổng Đóng Góp Theo Nhánh Phả Hệ (`TinhTongDongGop`):
- Khi một hộ gia đình cúng tiến, người đại diện có thể đăng ký chia đều hoặc ghi nhận cho từng cá nhân trong gia đình qua bảng `contribution_member_details`.
- Khi tính tổng công đức của một Chi nhánh, hệ thống dùng truy vấn đệ quy CTE (Common Table Expression) duyệt toàn bộ nhánh con cháu và cộng dồn `recorded_amount` của tất cả thành viên thuộc nhánh đó.

---

## 5. Luồng 5: Sổ Cái Bất Biến (Audit Trail) & Giao Dịch Bù Trừ

Nhằm đáp ứng yêu cầu kiểm toán tối cao NFR-01, bảng `immutable_ledgers` vận hành theo mô hình chuỗi ghi bất biến:

```mermaid
graph LR
    TX1["Bản ghi gốc: TX_001<br/>Amount: +50,000,000<br/>Type: SurplusTransfer"]
    TX2["Giao dịch bù trừ: TX_002<br/>Amount: -5,000,000<br/>Type: AdjustmentTransaction<br/>original_transaction_id: TX_001<br/>Reason: 'Tính nhầm hóa đơn âm thanh'"]
    
    TX2 -.->|Trỏ đệ quy sửa sai| TX1
```

### Quy tắc bất biến:
1. **Không Update / Delete**: Mọi hành vi cập nhật hay xóa bản ghi đều bị cấm ở cả tầng database trigger và repository layer.
2. **Giao dịch bù trừ (Adjustment Transaction)**: Khi phát hiện sai lệch số liệu sau khi sự kiện đã khóa sổ, Thủ quỹ lập một bút toán điều chỉnh mới mang giá trị chênh lệch (âm hoặc dương) và liên kết với `original_transaction_id` của giao dịch gốc.
3. **Phê duyệt 2 lớp**: Bút toán điều chỉnh chỉ có hiệu lực khi có chữ ký duyệt của Trưởng tộc (`approved_by_account_id IS NOT NULL`).

---

## 6. Luồng 6: Tạo Mã Mời Chung Gia Phả & Xem Trước Công Khai (US-16)

Để đơn giản hóa việc quy tụ con cháu xa quê gia nhập dòng họ, hệ thống cung cấp luồng sinh mã mời chung gia phả (`GeneralClanInvite`) có khả năng chia sẻ diện rộng:

```mermaid
sequenceDiagram
    autonumber
    actor ClanHead as Trưởng tộc (ROLE_CLAN_HEAD)
    participant API as InvitationController (/api/v1/invitations)
    participant Svc as InvitationService
    participant DB as PostgreSQL (invitation_codes)
    actor Guest as Con cháu / Thành viên mới

    ClanHead->>API: POST /api/v1/invitations/general (maxUses, validityDays, targetBranchId)
    API->>Svc: createGeneralClanInvite(request, principal)
    Svc->>Svc: Sinh mã 8 ký tự Base32 + URL chia sẻ đầy đủ
    Svc->>DB: Lưu bản ghi invitation_codes (invite_type='GeneralClanInvite')
    DB-->>API: Trả về InviteCodeResponse
    API-->>ClanHead: Mã mời & shareable_url (VD: http://localhost:3000/join?code=8K9P2X7N)
    
    Note over ClanHead,Guest: Trưởng tộc gửi mã/link vào nhóm Zalo/Facebook dòng họ
    
    Guest->>API: GET /api/v1/invitations/{code} (Công khai, không cần đăng nhập)
    API->>Svc: getInviteByCode(code)
    Svc->>DB: Truy vấn mã mời & kiểm tra hiệu lực
    DB-->>API: Trả về trạng thái hợp lệ, số lượt dùng còn lại
    API-->>Guest: Thông tin xem trước (Tên dòng họ, Chi nhánh) để chuẩn bị nộp đơn (US-17)
```

Chi tiết kiến trúc, giải thuật sinh mã an toàn mật mã, và quy trình kiểm thử E2E: xem tại **[`docs/user-stories/US-16-general-clan-invite.md`](../user-stories/US-16-general-clan-invite.md)**.

---

## 7. Luồng 7: Quản Lý Chi Phái, Phân Nhánh & Cấu Trúc Phân Cấp Gia Phả (US-04)

Phân hệ quản lý cây gia phả vận hành theo mô hình phân cấp đệ quy Adjacency List với ranh giới multi-tenancy nghiêm ngặt được tiêm tự động bởi `TenantFilter`:

```mermaid
sequenceDiagram
    autonumber
    actor Head as Trưởng tộc / Trưởng chi
    participant Filter as TenantFilter (OncePerRequestFilter)
    participant API as GenealogyController (/api/v1/branches)
    participant Svc as GenealogyService / Impl
    participant Val as GenealogyValidator
    participant DB as PostgreSQL (family_branches, members)

    Head->>Filter: Request + Header 'X-Clan-Id' (hoặc clanId query param)
    Filter->>Filter: TenantContext.setCurrentClanId(clanId)
    Filter->>API: Chuyển tiếp request đã được gán ngữ cảnh dòng họ
    
    alt Khởi tạo / Cập nhật Chi gốc
        API->>Svc: updateRootBranch(UpdateRootBranchRequest)
        Svc->>DB: Lưu/Cập nhật Root Branch (generationLevel = 1, parent = null)
        DB-->>API: Trả về BranchDetailResponse
    else Tạo nhánh con mới
        API->>Svc: createChildBranch(CreateChildBranchRequest)
        Svc->>DB: Tìm nhánh cha theo parentBranchId & clanId
        Svc->>Svc: Tự động gán child.generationLevel = parent.generationLevel + 1
        Svc->>DB: Lưu nhánh con
        DB-->>API: Trả về BranchDetailResponse (HTTP 201 Created)
    else Điều chỉnh nhánh cha (Reparenting)
        API->>Svc: updateBranch(branchId, UpdateBranchRequest)
        Svc->>Val: kiemTraChuTrinh(branchId, newParentId)
        Note over Val: Duyệt ngược chuỗi cha để chặn vòng lặp
        Svc->>Svc: Cascade cập nhật generationLevel cho toàn bộ cây con & members
        Svc->>DB: Lưu các thay đổi
        DB-->>API: Trả về BranchDetailResponse
    else Truy vấn cây phả hệ tối ưu In-Memory
        API->>Svc: getBranchTree()
        Svc->>DB: Query 1: Lấy toàn bộ branches theo clanId
        Svc->>DB: Query 2: GROUP BY branch_id đếm số members
        Svc->>Svc: Dựng cây đệ quy in-memory (Triệt tiêu N+1 queries)
        Svc-->>API: Trả về BranchTreeNodeResponse
    end
    API-->>Head: Envelope ApiResponse<T>
```

### Các nguyên tắc toàn vẹn nghiệp vụ (Business Invariants):
1. **Duy nhất Chi gốc**: Mỗi dòng họ có duy nhất một chi gốc với `generation_level = 1` và `parent_branch_id = NULL`.
2. **Chống chu trình**: Thuật toán `kiemTraChuTrinh` ngăn chặn tuyệt đối tình huống nhánh cha trở thành nhánh con của chính nó.
3. **Xóa an toàn**: Chặn xóa Chi gốc, chặn xóa nhánh đang có nhánh con hoặc đang có thành viên liên kết.
4. **Không N+1 Queries**: Lấy trọn vẹn cây gia phả chỉ trong đúng 2 truy vấn SQL và dựng cây trên RAM.

Chi tiết kiến trúc, thuật toán dựng cây và kịch bản kiểm thử: xem tại **[`docs/user-stories/US-04-child-branches-and-hierarchy.md`](../user-stories/US-04-child-branches-and-hierarchy.md)**.
