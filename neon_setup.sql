-- ==============================================================================
-- NEON POSTGRESQL 16+ SETUP SCRIPT (23 TABLES + MASTER DATA)
-- Hệ thống: Quản lý Quỹ Gia đình & Cây Phả hệ (Family Management Backend)
-- Tương thích 100% với Neon Serverless Postgres (neon.tech)
-- Chuẩn hóa từ đặc tả DBML trong Database.sql (Đã sửa kiểu dữ liệu nvarchar/datetime và khóa ngoại nghịch đảo)
-- ==============================================================================

BEGIN;

-- 0. KÍCH HOẠT EXTENSIONS POSTGRESQL CẦN THIẾT
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==============================================================================
-- PHÂN HỆ 1: CÂY GIA PHẢ, ĐỊNH DANH & MÃ MỜI (7 TABLES)
-- ==============================================================================

-- 1. FAMILY_BRANCH (Cấu trúc cây phả hệ phân cấp Chi/Nhánh)
CREATE TABLE IF NOT EXISTS family_branches (
    branch_id VARCHAR(20) PRIMARY KEY,
    clan_name VARCHAR(150),
    branch_name VARCHAR(100) NOT NULL,
    generation_level INT NOT NULL CHECK (generation_level >= 1),
    parent_branch_id VARCHAR(20) REFERENCES family_branches(branch_id) ON DELETE RESTRICT,
    representative_id VARCHAR(20),
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 2. MEMBER (Hồ sơ phả hệ cá nhân, bao gồm cả hồ sơ ảo US-18)
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

-- FK vòng cho family_branches.representative_id -> members(member_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_branch_representative'
    ) THEN
        ALTER TABLE family_branches
            ADD CONSTRAINT fk_branch_representative
            FOREIGN KEY (representative_id) REFERENCES members(member_id) ON DELETE SET NULL;
    END IF;
END $$;

-- 3. USER_ACCOUNT (Tài khoản người dùng trong hệ thống)
CREATE TABLE IF NOT EXISTS user_accounts (
    account_id VARCHAR(20) PRIMARY KEY,
    member_id VARCHAR(20) UNIQUE REFERENCES members(member_id) ON DELETE SET NULL,
    phone_number VARCHAR(15) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    account_status VARCHAR(25) NOT NULL DEFAULT 'Active'
        CHECK (account_status IN ('PendingApproval', 'Active', 'Suspended', 'Deactivated')),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 4. SYSTEM_ROLE (Danh mục vai trò hệ thống)
CREATE TABLE IF NOT EXISTS system_roles (
    role_id VARCHAR(20) PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(255)
);

-- 5. ACCOUNT_ROLE (Bảng nối tài khoản và vai trò)
CREATE TABLE IF NOT EXISTS account_roles (
    account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE CASCADE,
    role_id VARCHAR(20) NOT NULL REFERENCES system_roles(role_id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, role_id)
);

-- 6. INVITATION_CODE (Mã mời gia nhập hoặc mã liên kết vị trí phả hệ)
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
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 7. BRANCH_JOIN_REQUEST (Đề xuất tự định vị vị trí cây gia phả khi dùng General Invite)
CREATE TABLE IF NOT EXISTS branch_join_requests (
    request_id VARCHAR(20) PRIMARY KEY,
    account_id VARCHAR(20) UNIQUE NOT NULL REFERENCES user_accounts(account_id) ON DELETE CASCADE,
    invite_code VARCHAR(30) NOT NULL REFERENCES invitation_codes(invite_code) ON DELETE RESTRICT,
    full_name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('Male', 'Female', 'Other')),
    proposal_type VARCHAR(30) NOT NULL
        CHECK (proposal_type IN ('ExistingBranchLeaf', 'NewChildBranch')),
    target_branch_id VARCHAR(20) NOT NULL REFERENCES family_branches(branch_id) ON DELETE RESTRICT,
    proposed_new_branch_name VARCHAR(100),
    proposed_generation INT NOT NULL CHECK (proposed_generation >= 1),
    relationship_note VARCHAR(255),
    approval_status VARCHAR(20) NOT NULL DEFAULT 'Pending'
        CHECK (approval_status IN ('Pending', 'Approved', 'Rejected')),
    reviewed_by_account_id VARCHAR(20) REFERENCES user_accounts(account_id) ON DELETE SET NULL,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ
);

-- ==============================================================================
-- PHÂN HỆ 2: SỰ KIỆN, ĐĂNG KÝ & NHÂN SỰ (5 TABLES)
-- ==============================================================================

-- 8. EVENT (Sự kiện dòng họ: Giỗ tổ, Chạp mã, Tất niên, v.v.)
CREATE TABLE IF NOT EXISTS events (
    event_id VARCHAR(20) PRIMARY KEY,
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

-- 9. EVENT_PERSONNEL (Ban tổ chức và phân quyền theo ngữ cảnh từng sự kiện)
CREATE TABLE IF NOT EXISTS event_personnel (
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE CASCADE,
    event_role VARCHAR(30) NOT NULL
        CHECK (event_role IN ('HeadOfOrganizer', 'OrganizerMember', 'EventTreasurer')),
    is_read_only BOOLEAN DEFAULT FALSE,
    assigned_date TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, member_id, event_role)
);

-- 10. FAMILY_REGISTRATION (Đăng ký tham gia theo hộ gia đình)
CREATE TABLE IF NOT EXISTS family_registrations (
    registration_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    representative_member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    total_attendees INT NOT NULL CHECK (total_attendees > 0),
    vegetarian_meals INT DEFAULT 0 CHECK (vegetarian_meals >= 0),
    savory_meals INT DEFAULT 0 CHECK (savory_meals >= 0),
    dietary_requirements VARCHAR(255),
    transportation_notes VARCHAR(255),
    registration_date TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 11. REGISTRATION_MEMBER_DETAIL (Chi tiết từng cá nhân tham gia và điểm danh thực tế)
CREATE TABLE IF NOT EXISTS registration_member_details (
    registration_id VARCHAR(20) NOT NULL REFERENCES family_registrations(registration_id) ON DELETE CASCADE,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE CASCADE,
    is_checked_in BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (registration_id, member_id)
);

-- 12. EVENT_NOTIFICATION (Thông báo sự kiện và theo dõi biến động lịch trình)
CREATE TABLE IF NOT EXISTS event_notifications (
    notification_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    content_changes TEXT NOT NULL,
    sent_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    triggered_by VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT
);

-- ==============================================================================
-- PHÂN HỆ 3: PHÂN CÔNG & TIẾN ĐỘ CÔNG VIỆC (2 TABLES)
-- ==============================================================================

-- 13. EVENT_TASK (Hạng mục công việc chuẩn bị cho sự kiện)
CREATE TABLE IF NOT EXISTS event_tasks (
    task_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    task_name VARCHAR(100) NOT NULL,
    details TEXT,
    advance_budget DECIMAL(18,2) DEFAULT 0 CHECK (advance_budget >= 0),
    deadline TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Unassigned'
        CHECK (status IN ('Unassigned', 'InProgress', 'Completed', 'Overdue')),
    assigned_branch_id VARCHAR(20) REFERENCES family_branches(branch_id) ON DELETE SET NULL,
    assigned_member_id VARCHAR(20) REFERENCES members(member_id) ON DELETE SET NULL,
    created_by VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT
);

-- 14. TASK_PROGRESS_ALERT (Cảnh báo tiến độ tự động: 24h & leo thang 12h quá hạn)
CREATE TABLE IF NOT EXISTS task_progress_alerts (
    alert_id VARCHAR(20) PRIMARY KEY,
    task_id VARCHAR(20) NOT NULL REFERENCES event_tasks(task_id) ON DELETE CASCADE,
    alert_level VARCHAR(25) NOT NULL
        CHECK (alert_level IN ('Reminder24H', 'OverdueAlert12H')),
    recipient_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE CASCADE,
    alert_content TEXT NOT NULL,
    sent_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    is_read BOOLEAN DEFAULT FALSE
);

-- ==============================================================================
-- PHÂN HỆ 4: ĐÓNG GÓP & TÀI SẢN DÒNG HỌ (5 TABLES)
-- ==============================================================================

-- 15. CONTRIBUTION (Bảng cha quản lý mọi khoản đóng góp/công đức)
CREATE TABLE IF NOT EXISTS contributions (
    contribution_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    payer_member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    receiver_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    contribution_date TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    contribution_type VARCHAR(20) NOT NULL
        CHECK (contribution_type IN ('Cash', 'InKind')),
    total_monetary_value DECIMAL(18,2) NOT NULL CHECK (total_monetary_value >= 0)
);

-- 16. CONTRIBUTION_MEMBER_DETAIL (Phân bổ giá trị công đức cho từng thành viên)
CREATE TABLE IF NOT EXISTS contribution_member_details (
    contribution_id VARCHAR(20) NOT NULL REFERENCES contributions(contribution_id) ON DELETE CASCADE,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    recorded_amount DECIMAL(18,2) NOT NULL CHECK (recorded_amount >= 0),
    PRIMARY KEY (contribution_id, member_id)
);

-- 17. CASH_CONTRIBUTION (Bảng con chuyên biệt cho Tiền mặt / Chuyển khoản)
CREATE TABLE IF NOT EXISTS cash_contributions (
    contribution_id VARCHAR(20) PRIMARY KEY REFERENCES contributions(contribution_id) ON DELETE CASCADE,
    amount DECIMAL(18,2) NOT NULL CHECK (amount > 0),
    payment_method VARCHAR(20) NOT NULL
        CHECK (payment_method IN ('Cash', 'BankTransfer')),
    receipt_number VARCHAR(30) UNIQUE NOT NULL
);

-- 18. IN_KIND_CONTRIBUTION (Bảng con chuyên biệt cho Hiện vật)
CREATE TABLE IF NOT EXISTS in_kind_contributions (
    contribution_id VARCHAR(20) PRIMARY KEY REFERENCES contributions(contribution_id) ON DELETE CASCADE,
    item_name VARCHAR(100) NOT NULL,
    quantity DECIMAL(10,2) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(20) NOT NULL,
    estimated_value DECIMAL(18,2) DEFAULT 0 CHECK (estimated_value >= 0),
    conversion_method VARCHAR(255) NOT NULL,
    converted_monetary_value DECIMAL(18,2) NOT NULL CHECK (converted_monetary_value >= 0),
    processing_status VARCHAR(30) NOT NULL DEFAULT 'Pending'
        CHECK (processing_status IN ('Pending', 'Consumed', 'Returned', 'AddedToCommonAssets'))
);

-- 19. CLAN_ASSET (Tài sản tích lũy dòng họ sau sự kiện)
CREATE TABLE IF NOT EXISTS clan_assets (
    asset_id VARCHAR(20) PRIMARY KEY,
    asset_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit VARCHAR(20) NOT NULL,
    assessed_value DECIMAL(18,2) DEFAULT 0 CHECK (assessed_value >= 0),
    storage_location VARCHAR(255),
    origin_contribution_id VARCHAR(20) REFERENCES in_kind_contributions(contribution_id) ON DELETE SET NULL
);

-- ==============================================================================
-- PHÂN HỆ 5: TẠM ỨNG, QUYẾT TOÁN & SỔ CÁI BẤT BIẾN (4 TABLES)
-- ==============================================================================

-- 20. ADVANCE_VOUCHER (Phiếu tạm ứng kinh phí thực hiện công việc)
CREATE TABLE IF NOT EXISTS advance_vouchers (
    advance_voucher_id VARCHAR(20) PRIMARY KEY,
    task_id VARCHAR(20) NOT NULL REFERENCES event_tasks(task_id) ON DELETE RESTRICT,
    recipient_member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    advance_amount DECIMAL(18,2) NOT NULL CHECK (advance_amount > 0),
    received_date TIMESTAMPTZ NOT NULL,
    settlement_deadline TIMESTAMPTZ NOT NULL,
    commitment_notes TEXT,
    created_by_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT
);

-- 21. EXPENSE_SETTLEMENT_VOUCHER (Phiếu quyết toán hoàn ứng & đối soát hóa đơn chứng từ)
CREATE TABLE IF NOT EXISTS expense_settlement_vouchers (
    settlement_voucher_id VARCHAR(20) PRIMARY KEY,
    advance_voucher_id VARCHAR(20) UNIQUE NOT NULL REFERENCES advance_vouchers(advance_voucher_id) ON DELETE RESTRICT,
    actual_expense_amount DECIMAL(18,2) NOT NULL CHECK (actual_expense_amount >= 0),
    difference_amount DECIMAL(18,2) NOT NULL,
    invoice_attachment_url VARCHAR(500),
    expense_notes TEXT,
    treasurer_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    status VARCHAR(25) NOT NULL
        CHECK (status IN ('PendingReview', 'Valid', 'NeedsExplanation', 'Settled')),
    rejection_reason TEXT,
    settled_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 22. LEDGER_ADJUSTMENT_REQUEST (Quy trình đề xuất bù trừ sai sót sổ cái)
CREATE TABLE IF NOT EXISTS ledger_adjustment_requests (
    request_id VARCHAR(20) PRIMARY KEY,
    original_transaction_id VARCHAR(20) NOT NULL, -- FK thêm sau bảng immutable_ledgers
    proposed_adjustment_amount DECIMAL(18,2) NOT NULL,
    adjustment_reason TEXT NOT NULL,
    evidence_attachment_url VARCHAR(500),
    proposed_by_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'Pending'
        CHECK (approval_status IN ('Pending', 'Approved', 'Rejected')),
    reviewed_by_account_id VARCHAR(20) REFERENCES user_accounts(account_id) ON DELETE SET NULL,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ
);

-- 23. IMMUTABLE_LEDGER (Sổ cái quỹ chung bất biến - CHỈ GHI / INSERT-ONLY)
CREATE TABLE IF NOT EXISTS immutable_ledgers (
    transaction_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) REFERENCES events(event_id) ON DELETE SET NULL,
    adjustment_request_id VARCHAR(20) UNIQUE REFERENCES ledger_adjustment_requests(request_id) ON DELETE SET NULL,
    original_transaction_id VARCHAR(20) REFERENCES immutable_ledgers(transaction_id) ON DELETE RESTRICT,
    transaction_type VARCHAR(30) NOT NULL
        CHECK (transaction_type IN ('SurplusTransfer', 'AdjustmentTransaction')),
    amount DECIMAL(18,2) NOT NULL,
    running_balance DECIMAL(18,2) NOT NULL,
    description TEXT NOT NULL,
    created_by_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    approved_by_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    record_hash VARCHAR(64) UNIQUE NOT NULL, -- SHA-256 hash chaining audit trail
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- FK cho ledger_adjustment_requests.original_transaction_id -> immutable_ledgers(transaction_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_adjustment_original_tx'
    ) THEN
        ALTER TABLE ledger_adjustment_requests
            ADD CONSTRAINT fk_adjustment_original_tx
            FOREIGN KEY (original_transaction_id) REFERENCES immutable_ledgers(transaction_id) ON DELETE RESTRICT;
    END IF;
END $$;

-- ==============================================================================
-- INDEXES TỐI ƯU HÓA HIỆU NĂNG TRUY VẤN
-- ==============================================================================
CREATE INDEX IF NOT EXISTS idx_branch_parent ON family_branches(parent_branch_id);
CREATE INDEX IF NOT EXISTS idx_member_branch ON members(branch_id);
CREATE INDEX IF NOT EXISTS idx_member_status ON members(status);
CREATE INDEX IF NOT EXISTS idx_account_phone ON user_accounts(phone_number);
CREATE INDEX IF NOT EXISTS idx_account_email ON user_accounts(email);
CREATE INDEX IF NOT EXISTS idx_event_datetime ON events(event_datetime);
CREATE INDEX IF NOT EXISTS idx_event_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_task_event ON event_tasks(event_id);
CREATE INDEX IF NOT EXISTS idx_task_deadline ON event_tasks(deadline);
CREATE INDEX IF NOT EXISTS idx_contribution_event ON contributions(event_id);
CREATE INDEX IF NOT EXISTS idx_contribution_payer ON contributions(payer_member_id);
CREATE INDEX IF NOT EXISTS idx_ledger_created_at ON immutable_ledgers(created_at);

-- ==============================================================================
-- DỮ LIỆU KHỞI TẠO (SEED DATA)
-- ==============================================================================
INSERT INTO system_roles (role_id, role_name, description)
VALUES 
    ('ROLE_CLAN_HEAD', 'Trưởng Tộc', 'Quyền cao nhất dòng họ, phê duyệt thành viên mới, chi tiêu và chốt sổ cái quỹ chung'),
    ('ROLE_BRANCH_HEAD', 'Trưởng Chi / Nhánh', 'Quản lý cây phả hệ chi nhánh, phê duyệt đăng ký thành viên nhánh và đôn đốc công việc'),
    ('ROLE_MEMBER', 'Thành Viên Dòng Họ', 'Xem cây gia phả, đăng ký tham gia sự kiện gia đình, đóng góp công đức và nhận thông báo'),
    ('ROLE_ADMIN', 'Quản Trị Hệ Thống', 'Quản trị kỹ thuật, cấu hình tham số hạ tầng và phân quyền hệ thống')
ON CONFLICT (role_id) DO NOTHING;

COMMIT;
