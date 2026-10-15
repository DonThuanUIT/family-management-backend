-- ==============================================================================
-- V2: Seed System Roles & Initial Master Data
-- ==============================================================================

INSERT INTO system_roles (role_id, role_name, description)
VALUES 
    ('ROLE_CLAN_HEAD', 'Trưởng Tộc', 'Quyền cao nhất dòng họ, phê duyệt thành viên mới, chi tiêu và chốt sổ cái quỹ chung'),
    ('ROLE_BRANCH_HEAD', 'Trưởng Chi / Nhánh', 'Quản lý cây phả hệ chi nhánh, phê duyệt đăng ký thành viên nhánh và đôn đốc công việc'),
    ('ROLE_MEMBER', 'Thành Viên Dòng Họ', 'Xem cây gia phả, đăng ký tham gia sự kiện gia đình, đóng góp công đức và nhận thông báo'),
    ('ROLE_ADMIN', 'Quản Trị Hệ Thống', 'Quản trị kỹ thuật, cấu hình tham số hạ tầng và phân quyền hệ thống')
ON CONFLICT (role_id) DO NOTHING;

