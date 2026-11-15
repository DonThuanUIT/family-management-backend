-- ==============================================================================
-- V31: Seed initial asset audit log history for clan assets (US-52, Task #184)
-- ==============================================================================

INSERT INTO clan_asset_audits (
    audit_id, asset_id, audit_date, previous_quantity, actual_quantity,
    previous_condition, condition_status, storage_location, auditor_account_id, notes, created_at
)
VALUES
    (
        'ADT-2026-001',
        'AST-2026-001',
        NOW() - INTERVAL '15 days',
        1,
        1,
        'Good',
        'Good',
        'Gian thờ chính Từ Đường Dòng Họ',
        'ACC_TRUONG_TOC',
        'Kiểm kê định kỳ đầu năm Bính Ngọ 2026 - Bộ đỉnh đồng tam sự nguyên vẹn, sáng bóng trang nghiêm',
        NOW() - INTERVAL '15 days'
    ),
    (
        'ADT-2026-002',
        'AST-2026-002',
        NOW() - INTERVAL '12 days',
        1,
        1,
        'Good',
        'Good',
        'Tiền sảnh Từ Đường Dòng Họ Nguyễn (Đại Tôn)',
        'ACC_TRUONG_TOC',
        'Kiểm kê bảo quản định kỳ - Nước sơn son thếp vàng bóng đẹp, không cong vênh hay trầy xước',
        NOW() - INTERVAL '12 days'
    ),
    (
        'ADT-2026-003',
        'AST-2026-003',
        NOW() - INTERVAL '10 days',
        1,
        1,
        'Good',
        'Good',
        'Hương án Từ Đường Dòng Họ Nguyễn',
        'ACC_TRUONG_TOC',
        'Đồng ngũ sắc bảo quản chuẩn quy chuẩn di sản, hoa văn sắc nét không oxi hóa',
        NOW() - INTERVAL '10 days'
    ),
    (
        'ADT-2026-004',
        'AST-2026-004',
        NOW() - INTERVAL '8 days',
        2,
        2,
        'Good',
        'Good',
        'Hai bên hương án chính điện Từ Đường',
        'ACC_TRUONG_TOC',
        'Đôi lọ lục bình sứ Bát Tràng men rạn vẽ tứ linh toàn hảo, không nứt mẻ',
        NOW() - INTERVAL '8 days'
    ),
    (
        'ADT-2026-005',
        'AST-2026-005',
        NOW() - INTERVAL '5 days',
        1,
        1,
        'Good',
        'Good',
        'Gác chuông Từ Đường Dòng Họ',
        'ACC_TRUONG_TOC',
        'Kiểm tra định kỳ quai chuông và âm vang đại hồng chung, tình trạng tuyệt hảo',
        NOW() - INTERVAL '5 days'
    )
ON CONFLICT (audit_id) DO NOTHING;
