-- ==============================================================================
-- V6: Seed Demo Test Accounts, Branches, Members & Invitation Code (GTVN)
-- ==============================================================================

-- 1. Seed Initial Family Branches for CLAN_DEFAULT
INSERT INTO family_branches (branch_id, branch_name, generation_level, parent_branch_id, clan_id, description)
VALUES 
    ('BR_ROOT', 'Chi Gốc (Đại Tôn)', 1, NULL, 'CLAN_DEFAULT', 'Chi nhánh gốc khởi lập gia phả dòng họ'),
    ('BR_CHI_2', 'Chi 2 - Nhánh Giáp', 2, 'BR_ROOT', 'CLAN_DEFAULT', 'Chi nhánh thứ hai trực thuộc chi gốc')
ON CONFLICT (branch_id) DO NOTHING;

-- 2. Seed Member Profiles
INSERT INTO members (member_id, branch_id, full_name, date_of_birth, gender, generation, status, is_placeholder)
VALUES
    ('MEM_TRUONG_TOC', 'BR_ROOT', 'Nguyễn Trọng Hoàng', '1965-01-15', 'Male', 1, 'Living', FALSE),
    ('MEM_TRUONG_CHI', 'BR_CHI_2', 'Nguyễn Văn An', '1978-05-20', 'Male', 2, 'Living', FALSE),
    ('MEM_MAI_ANH', 'BR_CHI_2', 'Nguyễn Thị Mai Anh', '1992-10-22', 'Female', 3, 'Living', FALSE)
ON CONFLICT (member_id) DO NOTHING;

-- 3. Update Representative Members for Branches
UPDATE family_branches SET representative_id = 'MEM_TRUONG_TOC' WHERE branch_id = 'BR_ROOT';
UPDATE family_branches SET representative_id = 'MEM_TRUONG_CHI' WHERE branch_id = 'BR_CHI_2';

-- 4. Seed User Accounts (BCrypt hash for password: '123456')
-- Hash: $2a$10$Qm3GcpGYz4Vhu.P48QULFOL0Wx5PUi8hiz0eXipEDiM6ROYVDRpzm
INSERT INTO user_accounts (account_id, member_id, phone_number, email, password_hash, account_status)
VALUES
    ('ACC_TRUONG_TOC', 'MEM_TRUONG_TOC', '0901234567', 'truongtoc@gtvn.vn', '$2a$10$Qm3GcpGYz4Vhu.P48QULFOL0Wx5PUi8hiz0eXipEDiM6ROYVDRpzm', 'Active'),
    ('ACC_TRUONG_CHI', 'MEM_TRUONG_CHI', '0902345678', 'truongchi@gtvn.vn', '$2a$10$Qm3GcpGYz4Vhu.P48QULFOL0Wx5PUi8hiz0eXipEDiM6ROYVDRpzm', 'Active'),
    ('ACC_MAI_ANH', 'MEM_MAI_ANH', '0903888999', 'maianh@gtvn.vn', '$2a$10$Qm3GcpGYz4Vhu.P48QULFOL0Wx5PUi8hiz0eXipEDiM6ROYVDRpzm', 'Active')
ON CONFLICT (account_id) DO NOTHING;

-- 5. Seed Account Roles
INSERT INTO account_roles (account_id, role_id)
VALUES
    ('ACC_TRUONG_TOC', 'ROLE_CLAN_HEAD'),
    ('ACC_TRUONG_TOC', 'ROLE_MEMBER'),
    ('ACC_TRUONG_CHI', 'ROLE_BRANCH_HEAD'),
    ('ACC_TRUONG_CHI', 'ROLE_MEMBER'),
    ('ACC_MAI_ANH', 'ROLE_MEMBER')
ON CONFLICT (account_id, role_id) DO NOTHING;

-- 6. Seed Demo Invitation Code matching Image 3 (DK-CHI2-NHA-A-05)
INSERT INTO invitation_codes (
    invite_code, invite_type, clan_id, target_branch_id, 
    shareable_url, max_uses, used_count, expires_at, is_active, created_by_account_id
)
VALUES (
    'DK-CHI2-NHA-A-05',
    'GeneralClanInvite',
    'CLAN_DEFAULT',
    'BR_CHI_2',
    'https://gtvn.vn/invite/DK-CHI2-NHA-A-05',
    100,
    0,
    CURRENT_TIMESTAMP + INTERVAL '1000 days',
    TRUE,
    'ACC_TRUONG_TOC'
)
ON CONFLICT (invite_code) DO NOTHING;
