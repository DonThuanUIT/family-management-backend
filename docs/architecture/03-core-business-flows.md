# Các Luồng Nghiệp Vụ Cốt Lõi (Core Business Flows)

Tài liệu này mô tả chi tiết các luồng dữ liệu và quy trình nghiệp vụ xuyên suốt các phân hệ của hệ thống **Family Management Backend** (`familyfund-api`).

---

## 1. Luồng 1: Khởi Tạo Dòng Họ (US-01) & Xác Thực JWT Token (US-10)

Luồng cho phép Người khởi tạo thiết lập dòng họ mới một cách nguyên tử (atomic transaction) và đăng nhập hệ thống không trạng thái:

```mermaid
sequenceDiagram
    autonumber
    actor Creator as Người Khởi Tạo
    participant AuthAPI as AuthController (/api/v1/auth)
    participant BootSvc as GenealogyBootstrapService
    participant AuthSvc as AuthService
    participant JWT as JwtTokenProvider
    participant DB as PostgreSQL (clans, branches, members, accounts)

    Creator->>AuthAPI: POST /api/v1/auth/register-creator (họ tên, email, SĐT, pass, tên dòng họ, tên cụ thủy tổ)
    AuthAPI->>BootSvc: bootstrapClanWithCreator(command)
    BootSvc->>DB: Tạo Clan mới (clan_id = CLAN_...)
    BootSvc->>DB: Tạo Root Branch (BR_..., level = 1, parent = NULL)
    BootSvc->>DB: Tạo cụ Thủy tổ (MEM_..., generation = 1, is_placeholder = TRUE)
    BootSvc->>DB: Tạo Creator Member (MEM_..., generation = 2, branch = Root)
    BootSvc->>DB: Cập nhật Clan.founder_account_id & Root.representative_id
    AuthAPI->>AuthSvc: Tạo UserAccount (mật khẩu BCrypt) & gán ROLE_CLAN_HEAD
    AuthAPI-->>Creator: HTTP 201 Created (ApiResponse<AuthResponse>)

    Note over Creator,AuthAPI: Đăng nhập hệ thống lấy JWT Token
    Creator->>AuthAPI: POST /api/v1/auth/login (email/SĐT + mật khẩu)
    AuthAPI->>AuthSvc: login(request)
    AuthSvc->>DB: Kiểm tra tài khoản & đối soát BCrypt password
    AuthSvc->>JWT: generateAccessToken(claims: sub, clanId, memberId, fullName, roles)
    AuthSvc->>JWT: generateRefreshToken()
    AuthAPI-->>Creator: Access Token (1h) & Refresh Token (7d)
```

---

## 2. Luồng 2: Tạo Hồ Sơ Thành Viên Gia Phả & Chống Trùng (US-03)

Luồng thêm hồ sơ thành viên mới vào một chi nhánh cụ thể, bảo đảm tính toàn vẹn thế hệ và chống tạo trùng:

```mermaid
sequenceDiagram
    autonumber
    actor Head as Trưởng Tộc / Trưởng Chi
    participant Filter as TenantFilter & JwtAuthenticationFilter
    participant API as MemberController (/api/v1/branches/{branchId}/members)
    participant Svc as MemberServiceImpl
    participant DB as PostgreSQL (clans, family_branches, members)

    Head->>Filter: POST /branches/{branchId}/members + Bearer JWT
    Filter->>Filter: Xác thực JWT, nạp ROLE & clanId vào SecurityContext & TenantContext
    Filter->>API: Chuyển tiếp request đã xác thực
    API->>Svc: createMember(branchId, CreateMemberRequest, principal)
    Svc->>DB: SELECT * FROM clans WHERE clan_id = :clanId FOR UPDATE (Khóa bi quan chống race condition)
    Svc->>DB: Kiểm tra branchId thuộc về clanId
    Svc->>Svc: Kiểm tra quyền: CLAN_HEAD được thêm mọi chi; BRANCH_HEAD chỉ chi mình phụ trách
    Svc->>DB: Kiểm tra chống trùng (họ tên + năm sinh + giới tính + chi)
    Svc->>Svc: Gán generation = branch.generationLevel, is_placeholder = TRUE
    Svc->>DB: INSERT INTO members
    DB-->>API: Hồ sơ Member hoàn chỉnh
    API-->>Head: HTTP 201 Created (ApiResponse<MemberResponse>)
```

---

## 3. Luồng 3: Quản Lý Chi Phái, Phân Nhánh & Cấu Trúc Phân Cấp (US-04)

Phân hệ quản lý cây gia phả vận hành theo mô hình phân cấp đệ quy Adjacency List với thuật toán chống chu trình và dựng cây in-memory tối ưu không N+1:

```mermaid
sequenceDiagram
    autonumber
    actor Head as Trưởng tộc / Trưởng chi
    participant Filter as TenantFilter
    participant API as GenealogyController (/api/v1/branches)
    participant Svc as GenealogyServiceImpl
    participant Val as GenealogyValidator
    participant DB as PostgreSQL (family_branches, members)

    Head->>Filter: Request + Header 'X-Clan-Id' (hoặc clanId query param)
    Filter->>Filter: TenantContext.setCurrentClanId(clanId)
    Filter->>API: Chuyển tiếp request
    
    alt Tạo nhánh con mới
        API->>Svc: createChildBranch(CreateChildBranchRequest)
        Svc->>DB: Tìm nhánh cha theo parentBranchId & clanId
        Svc->>Svc: Tự động gán child.generationLevel = parent.generationLevel + 1
        Svc->>DB: Lưu nhánh con
        DB-->>API: BranchDetailResponse (HTTP 201 Created)
    else Điều chỉnh nhánh cha (Reparenting)
        API->>Svc: updateBranch(branchId, UpdateBranchRequest)
        Svc->>Val: kiemTraChuTrinh(branchId, newParentId)
        Note over Val: Duyệt ngược chuỗi cha để chặn vòng lặp đệ quy
        Svc->>Svc: Cascade cập nhật generationLevel cho toàn bộ cây con & members
        Svc->>DB: Lưu các thay đổi
        DB-->>API: BranchDetailResponse
    else Truy vấn cây phả hệ tối ưu In-Memory
        API->>Svc: getBranchTree()
        Svc->>DB: Query 1: Lấy toàn bộ branches theo clanId
        Svc->>DB: Query 2: GROUP BY branch_id đếm số members
        Svc->>Svc: Dựng cây đệ quy in-memory (Triệt tiêu hoàn toàn N+1 queries)
        Svc-->>API: BranchTreeNodeResponse
    end
    API-->>Head: ApiResponse<T>
```

---

## 4. Luồng 4: Tạo Mã Mời Chung Gia Phả & Xem Trước Công Khai (US-16)

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
    Svc->>Svc: Sinh mã 8 ký tự Base32 CSPRNG + URL chia sẻ đầy đủ
    Svc->>DB: Lưu bản ghi invitation_codes (invite_type='GeneralClanInvite')
    DB-->>API: Trả về InviteCodeResponse
    API-->>ClanHead: Mã mời & shareable_url (VD: http://localhost:3000/join?code=8K9P2X7N)
    
    Note over ClanHead,Guest: Trưởng tộc gửi mã/link vào nhóm Zalo/Facebook dòng họ
    
    Guest->>API: GET /api/v1/invitations/{code} (Công khai, không cần đăng nhập)
    API->>Svc: getInviteByCode(code)
    Svc->>DB: Truy vấn mã mời & kiểm tra hiệu lực
    DB-->>API: Trả về trạng thái hợp lệ, số lượt dùng còn lại
    API-->>Guest: Thông tin xem trước (Tên dòng họ, Chi nhánh) chuẩn bị nộp đơn (US-17)
```

---

## 5. Luồng 5: Đăng Ký Gia Nhập Dòng Họ Qua Mã Mời (Onboarding - US-17)

Quy trình tự do đăng ký tham gia dòng họ dành cho con cháu từ bên ngoài kèm cây thu gọn và xét duyệt vị trí phả hệ:

```mermaid
sequenceDiagram
    autonumber
    actor Guest as Người Dùng Mới
    actor BranchHead as Trưởng Chi / Trưởng Tộc
    participant JoinAPI as BranchJoinRequestController
    participant JoinSvc as BranchJoinRequestServiceImpl
    participant GenQuery as GenealogyQueryService
    participant GenCmd as GenealogyMemberCommandService
    participant DB as PostgreSQL (branch_join_requests, user_accounts)

    Guest->>JoinAPI: GET /api/v1/invitations/{code}/tree (Xem cây thu gọn công khai)
    JoinAPI->>JoinSvc: getOnboardingTree(code)
    JoinSvc->>GenQuery: getOnboardingTree(clanId)
    GenQuery-->>Guest: Danh sách nhánh lá & cấu trúc thu gọn

    Guest->>JoinAPI: POST /api/v1/invitations/{code}/join (SubmitJoinRequest: họ tên, SĐT, pass, proposalType, targetBranchId, newBranchName)
    JoinAPI->>JoinSvc: submitJoinRequest(code, request)
    JoinSvc->>DB: Tạo UserAccount (status = 'PendingApproval', BCrypt password)
    JoinSvc->>DB: Tạo BranchJoinRequest (status = 'Pending', proposal_type = 'ExistingLeaf'/'NewChildBranch')
    JoinAPI-->>Guest: HTTP 201 Created (JoinRequestResponse)

    BranchHead->>JoinAPI: GET /api/v1/join-requests?status=Pending (Xem danh sách đơn chờ duyệt)
    JoinAPI-->>BranchHead: Danh sách đơn chờ duyệt phân trang (lọc theo chi nếu là Trưởng chi)

    alt Phê duyệt đơn (Approve)
        BranchHead->>JoinAPI: PUT /api/v1/join-requests/{requestId}/approve
        JoinAPI->>JoinSvc: approveJoinRequest(requestId, principal)
        alt ProposalType = ExistingLeaf
            JoinSvc->>GenCmd: createMemberFromOnboarding(command)
        else ProposalType = NewChildBranch
            JoinSvc->>GenCmd: createBranchAndMemberFromOnboarding(command)
        end
        GenCmd->>DB: Tạo Member mới trong cây gia phả chính thức
        JoinSvc->>DB: Kích hoạt UserAccount (status = 'Active'), gán ROLE_MEMBER, liên kết memberId
        JoinSvc->>DB: Cập nhật BranchJoinRequest (approval_status = 'Approved')
        JoinAPI-->>BranchHead: Trả về đơn đã phê duyệt thành công
    else Từ chối đơn (Reject)
        BranchHead->>JoinAPI: PUT /api/v1/join-requests/{requestId}/reject (RejectJoinRequest: reason)
        JoinAPI->>JoinSvc: rejectJoinRequest(requestId, request, principal)
        JoinSvc->>DB: Cập nhật BranchJoinRequest (approval_status = 'Rejected', rejection_reason)
        JoinAPI-->>BranchHead: Trả về đơn đã từ chối
    end
```

---

## 6. Luồng 6: Quản Lý Vòng Đời Sự Kiện & Phân Quyền Theo Ngữ Cảnh (US-21)

Vòng đời sự kiện được quản lý theo máy trạng thái hữu hạn (FSM) kết hợp cơ chế kiểm soát quyền truy cập linh hoạt theo ngữ cảnh (`NhanSuSuKien`):

```mermaid
stateDiagram-v2
    [*] --> Draft: Trưởng ban tạo sự kiện (POST /events)
    Draft --> RegistrationOpen: Mở đăng ký hộ gia đình (Domain Event: EventOpened)
    RegistrationOpen --> RegistrationClosed: Hết hạn nhận đăng ký
    RegistrationClosed --> Ongoing: Bắt đầu diễn ra ngày giỗ / lễ hội
    Ongoing --> Ended: Hoàn tất lễ cúng & dọn dẹp
    Ended --> Closed: Quyết toán xong & Khóa sổ hoàn toàn
    Draft --> Cancelled: Hủy bỏ sự kiện
    RegistrationOpen --> Cancelled: Hủy bỏ sự kiện

    note right of Ongoing
      Contextual RBAC:
      - HeadOfOrganizer: Quản trị sự kiện
      - EventTreasurer: Quản lý thu chi, lập phiếu
      - OrganizerMember: Thành viên ban tổ chức
    end note

    note right of Closed
      CHẾ ĐỘ CHỈ ĐỌC (Read-Only):
      Chặn tuyệt đối mọi thao tác thêm/sửa/xóa
      phiếu chi, tạm ứng, thu tiền, nhân sự.
    end note
```

- **Xác thực quyền theo ngữ cảnh**: Middleware `@Component("nhanSuSuKien")` với hàm `xacThucQuyenTheoNguCanh(#eventId, 'HeadOfOrganizer', 'EventTreasurer')` được nhúng trực tiếp vào `@PreAuthorize` trên các controller methods.
- **Ràng buộc nhân sự**: Mỗi sự kiện có tối đa 1 Trưởng ban tổ chức (`HeadOfOrganizer`) và 1 Thủ quỹ sự kiện (`EventTreasurer`). Danh tính nhân sự được kiểm tra hợp lệ với hồ sơ gia phả qua `GenealogyQueryService`.

---

## 7. Luồng 7: Thông Báo Sự Kiện Đa Kênh & Tự Động So Sánh Lịch Trình (US-22)

```mermaid
sequenceDiagram
    autonumber
    actor Organizer as Ban Tổ Chức
    participant API as EventController (/api/v1/events)
    participant Svc as EventServiceImpl
    participant Pub as ApplicationEventPublisher
    participant Listener as EventNotificationListener (@Async)
    participant Sender as NotificationSender (LoggingAdapter)
    participant DB as PostgreSQL (event_notifications)

    alt Chuyển trạng thái sang RegistrationOpen
        Organizer->>API: PUT /events/{id}/status (status = 'RegistrationOpen')
        API->>Svc: updateEventStatus(id, RegistrationOpen)
        Svc->>Pub: publishEvent(new EventOpenedDomainEvent(eventId, eventName, clanId,...))
    else Cập nhật ngày giờ / địa điểm có sai khác (Diff)
        Organizer->>API: PUT /events/{id} (UpdateEventRequest: startDate, endDate, location)
        API->>Svc: updateEvent(id, request)
        Svc->>Svc: So sánh diff ngày giờ / địa điểm
        opt Phát hiện thay đổi lịch trình
            Svc->>Pub: publishEvent(new EventScheduleChangedDomainEvent(eventId, diffDetails,...))
        end
    end

    Note over Pub,Listener: Xử lý bất đồng bộ (@Async) không chặn HTTP response
    Pub-->>Listener: Lắng nghe Domain Events
    Listener->>DB: Tạo bản ghi lưu vết event_notifications (ID: NTF...)
    Listener->>Sender: sendNotification(notification)
    Sender-->>Sender: Ghi log / phát thông báo đẩy tới toàn bộ thành viên dòng họ
```

---

## 8. Luồng 8: Tạm Ứng, Quyết Toán & Cơ Chế Chặn Khóa Sổ

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

## 9. Luồng 9: Đóng Góp Quỹ Họ & Phân Bổ Công Đức Đệ Quy

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

## 10. Luồng 10: Sổ Cái Bất Biến (Audit Trail) & Giao Dịch Bù Trừ

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
