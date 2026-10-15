-- ==============================================================================
-- Database Schema for Family Management Backend (familyfund-api)
-- Engine: PostgreSQL 16+ (with pgvector support)
-- ==============================================================================

-- 0. Enable Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "vector";

-- ==============================================================================
-- PHÂN HỆ 1: CÂY GIA PHẢ & ĐỊNH DANH (FAMILY TREE & IDENTITY)
-- ==============================================================================

-- 1. Bảng CLAN (Quản lý dòng họ - Hỗ trợ Multi-tenant)
CREATE TABLE IF NOT EXISTS clans (
    clan_id VARCHAR(20) PRIMARY KEY,
    clan_name VARCHAR(100) NOT NULL,
    established_date DATE,
    invite_code VARCHAR(50) UNIQUE,
    founder_account_id VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bảng FAMILY_BRANCH (Cấu trúc phân cấp Chi/Nhánh)
CREATE TABLE IF NOT EXISTS family_branches (
    branch_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE,
    branch_name VARCHAR(100) NOT NULL,
    generation_level INTEGER NOT NULL CHECK (generation_level >= 1),
    parent_branch_id VARCHAR(20) REFERENCES family_branches(branch_id) ON DELETE RESTRICT,
    representative_id VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Bảng MEMBER (Hồ sơ phả hệ cá nhân)
CREATE TABLE IF NOT EXISTS members (
    member_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE,
    full_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20),
    email VARCHAR(100),
    date_of_birth DATE,
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('Male', 'Female', 'Other')),
    generation INTEGER NOT NULL CHECK (generation >= 1),
    status VARCHAR(20) NOT NULL DEFAULT 'Living' CHECK (status IN ('Living', 'Deceased')),
    branch_id VARCHAR(20) REFERENCES family_branches(branch_id) ON DELETE SET NULL,
    claim_code VARCHAR(50) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Bảng USER_ACCOUNT (Cấp quyền đăng nhập hệ thống)
CREATE TABLE IF NOT EXISTS user_accounts (
    account_id VARCHAR(20) PRIMARY KEY,
    member_id VARCHAR(20) NOT NULL UNIQUE REFERENCES members(member_id) ON DELETE CASCADE,
    login_identifier VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Bảng SYSTEM_ROLE (Danh mục vai trò toàn cục - Global Roles)
CREATE TABLE IF NOT EXISTS system_roles (
    role_id VARCHAR(50) PRIMARY KEY,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(255)
);

-- 6. Bảng ACCOUNT_ROLE (Bảng trung gian phân quyền toàn cục)
CREATE TABLE IF NOT EXISTS account_roles (
    account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE CASCADE,
    role_id VARCHAR(50) NOT NULL REFERENCES system_roles(role_id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, role_id)
);

-- 7. Bảng JOIN_REQUEST (Quản lý yêu cầu xin gia nhập dòng họ)
CREATE TABLE IF NOT EXISTS join_requests (
    request_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE,
    applicant_name VARCHAR(100) NOT NULL,
    applicant_phone VARCHAR(20) NOT NULL,
    proposed_branch_id VARCHAR(20) REFERENCES family_branches(branch_id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Pending' CHECK (status IN ('Pending', 'Approved', 'Rejected')),
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by_account_id VARCHAR(20) REFERENCES user_accounts(account_id) ON DELETE SET NULL
);

-- Thiết lập các khóa ngoại vòng (Circular FKs) sau khi các bảng đã khởi tạo
ALTER TABLE clans 
    DROP CONSTRAINT IF EXISTS fk_clans_founder;
ALTER TABLE clans 
    ADD CONSTRAINT fk_clans_founder FOREIGN KEY (founder_account_id) 
    REFERENCES user_accounts(account_id) ON DELETE SET NULL;

ALTER TABLE family_branches 
    DROP CONSTRAINT IF EXISTS fk_family_branches_rep;
ALTER TABLE family_branches 
    ADD CONSTRAINT fk_family_branches_rep FOREIGN KEY (representative_id) 
    REFERENCES members(member_id) ON DELETE SET NULL;


-- ==============================================================================
-- PHÂN HỆ 2: QUẢN LÝ SỰ KIỆN, ĐĂNG KÝ & NHÂN SỰ (EVENTS, REGISTRATION & DYNAMIC RBAC)
-- ==============================================================================

-- 8. Bảng EVENT (Khởi tạo và quản lý sự kiện)
CREATE TABLE IF NOT EXISTS events (
    event_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE,
    event_name VARCHAR(100) NOT NULL,
    event_datetime TIMESTAMP WITH TIME ZONE NOT NULL,
    location VARCHAR(255) NOT NULL,
    estimated_budget NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (estimated_budget >= 0),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'Draft' CHECK (status IN ('Draft', 'RegistrationOpen', 'Ongoing', 'Closed')),
    created_by VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 9. Bảng EVENT_TASK (Hạng mục công việc sự kiện)
CREATE TABLE IF NOT EXISTS event_tasks (
    task_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    task_name VARCHAR(150) NOT NULL,
    description TEXT,
    assigned_member_id VARCHAR(20) REFERENCES members(member_id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Pending' CHECK (status IN ('Pending', 'InProgress', 'Completed', 'Cancelled')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 10. Bảng EVENT_PERSONNEL (Phân quyền động theo sự kiện - Contextual RBAC)
CREATE TABLE IF NOT EXISTS event_personnel (
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE CASCADE,
    event_role VARCHAR(50) NOT NULL CHECK (event_role IN ('HeadOfOrganizer', 'OrganizerMember', 'EventTreasurer')),
    assigned_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, member_id, event_role)
);

-- 11. Bảng FAMILY_REGISTRATION (Đơn đăng ký tham dự sự kiện của hộ gia đình)
CREATE TABLE IF NOT EXISTS family_registrations (
    registration_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    representative_member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    total_attendees INTEGER NOT NULL DEFAULT 1 CHECK (total_attendees > 0),
    dietary_requirements VARCHAR(255),
    transportation_notes VARCHAR(255),
    registration_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 12. Bảng REGISTRATION_MEMBER_DETAIL (Danh tính cá nhân chi tiết đi dự sự kiện)
CREATE TABLE IF NOT EXISTS registration_member_details (
    registration_id VARCHAR(20) NOT NULL REFERENCES family_registrations(registration_id) ON DELETE CASCADE,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    PRIMARY KEY (registration_id, member_id)
);

-- 13. Bảng EVENT_NOTIFICATION (Nhật ký thông báo biến động sự kiện)
CREATE TABLE IF NOT EXISTS event_notifications (
    notification_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    content_changes TEXT NOT NULL,
    sent_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    triggered_by VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT
);


-- ==============================================================================
-- PHÂN HỆ 3: TẠM ỨNG, QUYẾT TOÁN & SỔ CÁI BẤT BIẾN (FINANCE & IMMUTABLE LEDGER)
-- ==============================================================================

-- 14. Bảng ADVANCE_VOUCHER (Phiếu cấp phát kinh phí tạm ứng)
CREATE TABLE IF NOT EXISTS advance_vouchers (
    advance_voucher_id VARCHAR(20) PRIMARY KEY,
    task_id VARCHAR(20) NOT NULL REFERENCES event_tasks(task_id) ON DELETE RESTRICT,
    recipient_member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    advance_amount NUMERIC(18, 2) NOT NULL CHECK (advance_amount > 0),
    received_date TIMESTAMP WITH TIME ZONE NOT NULL,
    settlement_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    commitment_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 15. Bảng EXPENSE_SETTLEMENT_VOUCHER (Phiếu chi quyết toán & đối soát)
CREATE TABLE IF NOT EXISTS expense_settlement_vouchers (
    settlement_voucher_id VARCHAR(20) PRIMARY KEY,
    advance_voucher_id VARCHAR(20) NOT NULL REFERENCES advance_vouchers(advance_voucher_id) ON DELETE RESTRICT,
    actual_expense_amount NUMERIC(18, 2) NOT NULL CHECK (actual_expense_amount >= 0),
    difference_amount NUMERIC(18, 2) NOT NULL,
    treasurer_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL CHECK (status IN ('Valid', 'NeedsExplanation')),
    rejection_reason TEXT,
    settlement_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 16. Bảng IMMUTABLE_LEDGER (Sổ cái Quỹ chung bất biến - Audit Trail NFR-01)
CREATE TABLE IF NOT EXISTS immutable_ledgers (
    transaction_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) REFERENCES events(event_id) ON DELETE SET NULL,
    transaction_type VARCHAR(50) NOT NULL CHECK (transaction_type IN ('SurplusTransfer', 'AdjustmentTransaction', 'InitialFund', 'DirectDonation')),
    amount NUMERIC(18, 2) NOT NULL,
    original_transaction_id VARCHAR(20) REFERENCES immutable_ledgers(transaction_id) ON DELETE RESTRICT,
    adjustment_reason TEXT,
    created_by_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    approved_by_account_id VARCHAR(20) REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


-- ==============================================================================
-- PHÂN HỆ 4: ĐÓNG GÓP QUỸ HỌ & HIỆN VẬT (CONTRIBUTIONS & CLAN ASSETS)
-- ==============================================================================

-- 17. Bảng CONTRIBUTION (Thực thể gốc ghi nhận đóng góp/công đức)
CREATE TABLE IF NOT EXISTS contributions (
    contribution_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE RESTRICT,
    payer_member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    receiver_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    contribution_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    contribution_type VARCHAR(20) NOT NULL CHECK (contribution_type IN ('Cash', 'InKind'))
);

-- 18. Bảng CONTRIBUTION_MEMBER_DETAIL (Danh tính các cá nhân cùng đứng tên công đức)
CREATE TABLE IF NOT EXISTS contribution_member_details (
    contribution_id VARCHAR(20) NOT NULL REFERENCES contributions(contribution_id) ON DELETE CASCADE,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    recorded_amount NUMERIC(18, 2) NOT NULL CHECK (recorded_amount >= 0),
    PRIMARY KEY (contribution_id, member_id)
);

-- 19. Bảng CASH_CONTRIBUTION (Thực thể con - Đóng góp bằng tiền mặt/chuyển khoản)
CREATE TABLE IF NOT EXISTS cash_contributions (
    contribution_id VARCHAR(20) PRIMARY KEY REFERENCES contributions(contribution_id) ON DELETE CASCADE,
    amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('Cash', 'BankTransfer')),
    receipt_number VARCHAR(50) NOT NULL UNIQUE
);

-- 20. Bảng IN_KIND_CONTRIBUTION (Thực thể con - Đóng góp bằng lễ vật/hiện vật)
CREATE TABLE IF NOT EXISTS in_kind_contributions (
    contribution_id VARCHAR(20) PRIMARY KEY REFERENCES contributions(contribution_id) ON DELETE CASCADE,
    item_name VARCHAR(100) NOT NULL,
    quantity NUMERIC(10, 2) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(20) NOT NULL,
    estimated_value NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (estimated_value >= 0),
    conversion_method VARCHAR(255),
    converted_monetary_value NUMERIC(18, 2) NOT NULL CHECK (converted_monetary_value >= 0),
    processing_status VARCHAR(30) NOT NULL CHECK (processing_status IN ('Consumed', 'Returned', 'AddedToCommonAssets'))
);

-- 21. Bảng CLAN_ASSET (Danh mục tài sản chung lũy kế dòng họ)
CREATE TABLE IF NOT EXISTS clan_assets (
    asset_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE,
    asset_name VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    unit VARCHAR(20) NOT NULL,
    assessed_value NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (assessed_value >= 0),
    origin_contribution_id VARCHAR(20) REFERENCES in_kind_contributions(contribution_id) ON DELETE SET NULL,
    acquired_date DATE DEFAULT CURRENT_DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


-- ==============================================================================
-- INDEXES FOR PERFORMANCE & INTEGRITY
-- ==============================================================================

-- Phân hệ 1
CREATE INDEX IF NOT EXISTS idx_members_clan ON members(clan_id);
CREATE INDEX IF NOT EXISTS idx_members_branch ON members(branch_id);
CREATE INDEX IF NOT EXISTS idx_members_phone ON members(phone_number);
CREATE INDEX IF NOT EXISTS idx_members_status ON members(status);
CREATE INDEX IF NOT EXISTS idx_family_branches_parent ON family_branches(parent_branch_id);
CREATE INDEX IF NOT EXISTS idx_user_accounts_login ON user_accounts(login_identifier);

-- Phân hệ 2
CREATE INDEX IF NOT EXISTS idx_events_clan ON events(clan_id);
CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_events_datetime ON events(event_datetime);
CREATE INDEX IF NOT EXISTS idx_event_tasks_event ON event_tasks(event_id);
CREATE INDEX IF NOT EXISTS idx_family_reg_event ON family_registrations(event_id);

-- Phân hệ 3
CREATE INDEX IF NOT EXISTS idx_advance_task ON advance_vouchers(task_id);
CREATE INDEX IF NOT EXISTS idx_settlement_advance ON expense_settlement_vouchers(advance_voucher_id);
CREATE INDEX IF NOT EXISTS idx_ledger_event ON immutable_ledgers(event_id);
CREATE INDEX IF NOT EXISTS idx_ledger_orig_tx ON immutable_ledgers(original_transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_created_at ON immutable_ledgers(created_at);

-- Phân hệ 4
CREATE INDEX IF NOT EXISTS idx_contributions_event ON contributions(event_id);
CREATE INDEX IF NOT EXISTS idx_contributions_payer ON contributions(payer_member_id);
CREATE INDEX IF NOT EXISTS idx_contrib_detail_member ON contribution_member_details(member_id);
CREATE INDEX IF NOT EXISTS idx_clan_assets_clan ON clan_assets(clan_id);


-- ==============================================================================
-- INITIAL SEED DATA (CORE ROLES & DEMO DATA)
-- ==============================================================================

-- System Roles
INSERT INTO system_roles (role_id, role_name, description) VALUES
('ROLE_ADMIN', 'Quản trị viên Hệ thống', 'Toàn quyền cấu hình và quản trị hệ thống'),
('ROLE_CLAN_HEAD', 'Trưởng tộc / Trưởng họ', 'Quản lý toàn bộ thông tin dòng họ, duyệt chi và phê chuẩn sổ cái'),
('ROLE_BRANCH_HEAD', 'Trưởng Chi / Trưởng Nhánh', 'Quản lý danh sách thành viên và các hoạt động của chi nhánh'),
('ROLE_MEMBER', 'Thành viên dòng họ', 'Xem cây gia phả, đăng ký sự kiện và theo dõi đóng góp')
ON CONFLICT (role_id) DO NOTHING;

-- Demo Clan: Dòng Họ Nguyễn Trọng
INSERT INTO clans (clan_id, clan_name, established_date, invite_code) VALUES
('CLAN_NGUYENTRONG', 'Dòng họ Nguyễn Trọng - Làng Cổ Am', '1850-01-01', 'CLAN-NGUYEN-1850')
ON CONFLICT (clan_id) DO NOTHING;

-- Demo Branches
INSERT INTO family_branches (branch_id, clan_id, branch_name, generation_level, parent_branch_id) VALUES
('BRANCH_ROOT', 'CLAN_NGUYENTRONG', 'Nhánh Gốc (Cụ Tổ Khởi Sinh)', 1, NULL),
('BRANCH_CHI1', 'CLAN_NGUYENTRONG', 'Chi Trưởng (Chi 1)', 2, 'BRANCH_ROOT'),
('BRANCH_CHI2', 'CLAN_NGUYENTRONG', 'Chi Thứ (Chi 2)', 2, 'BRANCH_ROOT')
ON CONFLICT (branch_id) DO NOTHING;

-- Demo Member (Trưởng Tộc)
INSERT INTO members (member_id, clan_id, full_name, phone_number, email, date_of_birth, gender, generation, status, branch_id) VALUES
('MEM_001', 'CLAN_NGUYENTRONG', 'Nguyễn Trọng Hoàng', '0901234567', 'hoang.nguyen@familyfund.vn', '1965-05-15', 'Male', 4, 'Living', 'BRANCH_CHI1'),
('MEM_002', 'CLAN_NGUYENTRONG', 'Nguyễn Trọng Anh', '0912345678', 'anh.nguyen@familyfund.vn', '1970-08-20', 'Male', 4, 'Living', 'BRANCH_CHI2')
ON CONFLICT (member_id) DO NOTHING;

-- Update Representative for Branch
UPDATE family_branches SET representative_id = 'MEM_001' WHERE branch_id = 'BRANCH_CHI1';
UPDATE family_branches SET representative_id = 'MEM_002' WHERE branch_id = 'BRANCH_CHI2';

-- Demo User Account (Trưởng tộc) - Password hash: bcrypt for 'Admin@123'
INSERT INTO user_accounts (account_id, member_id, login_identifier, password_hash, is_active) VALUES
('ACC_001', 'MEM_001', 'admin_hoang', '$2a$10$wTf4EwD8rK8x1wz2a8wQveb6XhC6yqg7Q0o4Y4eDkFqI7eE9xJk1a', TRUE)
ON CONFLICT (account_id) DO NOTHING;

-- Update founder for Clan
UPDATE clans SET founder_account_id = 'ACC_001' WHERE clan_id = 'CLAN_NGUYENTRONG';

-- Assign Roles
INSERT INTO account_roles (account_id, role_id) VALUES
('ACC_001', 'ROLE_ADMIN'),
('ACC_001', 'ROLE_CLAN_HEAD')
ON CONFLICT (account_id, role_id) DO NOTHING;

