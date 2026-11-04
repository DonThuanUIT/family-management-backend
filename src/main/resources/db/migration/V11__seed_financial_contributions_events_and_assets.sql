-- ==============================================================================
-- V11: Seed Real Financial Contributions, Event, Personnel, and Clan Assets (US-09 & US-10)
-- Clan: CLAN_DEFAULT (Dòng họ Nguyễn Tộc - Đại Tôn)
-- ==============================================================================

-- 1. Cập nhật thông tin nhận diện Dòng Họ Nguyễn Tộc cho CLAN_DEFAULT
UPDATE clans 
SET clan_name = 'Dòng họ Nguyễn Tộc (Đại Tôn)',
    ancestor_name = 'Nguyễn Khởi Tổ',
    origin_location = 'Từ Đường Dòng Họ Nguyễn (Đại Tôn)',
    established_year = 1845,
    description = 'Dòng họ Nguyễn Tộc khởi lập từ đường lưu truyền gia phong, gìn giữ cội nguồn muôn đời thịnh vượng.'
WHERE clan_id = 'CLAN_DEFAULT';

-- 2. Khởi tạo Sự Kiện Dòng Họ Thường Niên
INSERT INTO events (
    event_id, clan_id, event_name, event_datetime, event_date_lunar, 
    location, estimated_budget, description, status, created_by
)
VALUES (
    'EVT-GIO-TO-2026',
    'CLAN_DEFAULT',
    'Đại Lễ Giỗ Tổ Thủy Tộc Xuân Bính Ngọ 2026',
    '2026-03-28 08:00:00+07',
    '10/03 Bính Ngọ',
    'Từ Đường Dòng Họ Nguyễn (Đại Tôn)',
    50000000.00,
    'Đại lễ giỗ tổ thường niên toàn thể con cháu các chi phái tụ hội dâng hương tế lễ, tri ân tiên tổ và tiếp nhận đóng góp xây dựng quỹ gia tộc.',
    'Ongoing',
    'ACC_TRUONG_TOC'
)
ON CONFLICT (event_id) DO UPDATE SET
    event_name = EXCLUDED.event_name,
    location = EXCLUDED.location,
    status = EXCLUDED.status;

-- 3. Phân công Ban Tổ Chức & Phân Quyền Dual-RBAC Theo Ngữ Cảnh Sự Kiện
INSERT INTO event_personnel (event_id, member_id, event_role, is_read_only)
VALUES
    ('EVT-GIO-TO-2026', 'MEM_TRUONG_TOC', 'HeadOfOrganizer', FALSE),
    ('EVT-GIO-TO-2026', 'MEM_TRUONG_CHI', 'EventTreasurer', FALSE),
    ('EVT-GIO-TO-2026', 'MEM_MAI_ANH', 'OrganizerMember', FALSE)
ON CONFLICT (event_id, member_id, event_role) DO NOTHING;

-- 4. Bảng Định Mức Giá Hiện Vật Cúng Tiến (Event Item Benchmarks)
INSERT INTO event_item_benchmarks (benchmark_id, event_id, clan_id, item_name, unit, unit_price, note)
VALUES
    ('BM-2026-HEO-QUAY', 'EVT-GIO-TO-2026', 'CLAN_DEFAULT', 'Heo quay nguyên con dâng lễ tế tổ', 'con', 4000000.00, 'Định mức heo quay loại 1 dâng cúng tế tổ'),
    ('BM-2026-XOI-GA', 'EVT-GIO-TO-2026', 'CLAN_DEFAULT', 'Mâm xôi gấc & gà trống thiến tế tổ', 'mâm', 500000.00, 'Định mức 1 mâm cúng tiêu chuẩn')
ON CONFLICT (benchmark_id) DO NOTHING;

-- 5. Phiếu Đóng Góp Tiền Mặt & Chuyển Khoản (Cash Contributions)
-- 5.1. Phiếu 1: Cụ Trưởng Tộc Nguyễn Trọng Hoàng cúng tiến 10.000.000 đ qua chuyển khoản
INSERT INTO contributions (
    contribution_id, event_id, payer_member_id, receiver_account_id, 
    contribution_date, contribution_type, total_monetary_value
)
VALUES (
    'CTB-CASH-001',
    'EVT-GIO-TO-2026',
    'MEM_TRUONG_TOC',
    'ACC_TRUONG_CHI',
    CURRENT_TIMESTAMP - INTERVAL '4 hours',
    'Cash',
    10000000.00
)
ON CONFLICT (contribution_id) DO NOTHING;

INSERT INTO cash_contributions (contribution_id, amount, payment_method, receipt_number)
VALUES (
    'CTB-CASH-001',
    10000000.00,
    'BankTransfer',
    'PT-20260328-09482'
)
ON CONFLICT (contribution_id) DO NOTHING;

INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
VALUES (
    'CTB-CASH-001',
    'MEM_TRUONG_TOC',
    10000000.00
)
ON CONFLICT (contribution_id, member_id) DO NOTHING;

-- 5.2. Phiếu 2: Bác Trưởng Chi 2 Nguyễn Văn An cúng tiến 5.000.000 đ tiền mặt
INSERT INTO contributions (
    contribution_id, event_id, payer_member_id, receiver_account_id, 
    contribution_date, contribution_type, total_monetary_value
)
VALUES (
    'CTB-CASH-002',
    'EVT-GIO-TO-2026',
    'MEM_TRUONG_CHI',
    'ACC_TRUONG_CHI',
    CURRENT_TIMESTAMP - INTERVAL '3 hours',
    'Cash',
    5000000.00
)
ON CONFLICT (contribution_id) DO NOTHING;

INSERT INTO cash_contributions (contribution_id, amount, payment_method, receipt_number)
VALUES (
    'CTB-CASH-002',
    5000000.00,
    'Cash',
    'PT-20260328-08114'
)
ON CONFLICT (contribution_id) DO NOTHING;

INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
VALUES (
    'CTB-CASH-002',
    'MEM_TRUONG_CHI',
    5000000.00
)
ON CONFLICT (contribution_id, member_id) DO NOTHING;

-- 5.3. Phiếu 3: Thành viên Nguyễn Thị Mai Anh cúng tiến 2.000.000 đ qua chuyển khoản
INSERT INTO contributions (
    contribution_id, event_id, payer_member_id, receiver_account_id, 
    contribution_date, contribution_type, total_monetary_value
)
VALUES (
    'CTB-CASH-003',
    'EVT-GIO-TO-2026',
    'MEM_MAI_ANH',
    'ACC_TRUONG_CHI',
    CURRENT_TIMESTAMP - INTERVAL '2 hours',
    'Cash',
    2000000.00
)
ON CONFLICT (contribution_id) DO NOTHING;

INSERT INTO cash_contributions (contribution_id, amount, payment_method, receipt_number)
VALUES (
    'CTB-CASH-003',
    2000000.00,
    'BankTransfer',
    'PT-20260328-07205'
)
ON CONFLICT (contribution_id) DO NOTHING;

INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
VALUES (
    'CTB-CASH-003',
    'MEM_MAI_ANH',
    2000000.00
)
ON CONFLICT (contribution_id, member_id) DO NOTHING;

-- 6. Phiếu Đóng Góp Hiện Vật (In-Kind Contributions)
-- 6.1. Phiếu hiện vật 1: Trưởng chi Nguyễn Văn An cúng tiến Bộ đỉnh đồng tam sự (Chuyển thành Bảo vật Từ Đường)
INSERT INTO contributions (
    contribution_id, event_id, payer_member_id, receiver_account_id, 
    contribution_date, contribution_type, total_monetary_value
)
VALUES (
    'CTB-INKIND-001',
    'EVT-GIO-TO-2026',
    'MEM_TRUONG_CHI',
    'ACC_TRUONG_CHI',
    CURRENT_TIMESTAMP - INTERVAL '5 hours',
    'InKind',
    18000000.00
)
ON CONFLICT (contribution_id) DO NOTHING;

INSERT INTO in_kind_contributions (
    contribution_id, item_name, quantity, unit, estimated_value, 
    conversion_method, converted_monetary_value, processing_status, receipt_number
)
VALUES (
    'CTB-INKIND-001',
    'Bộ đỉnh đồng tam sự chạm khắc song long',
    1.0,
    'bộ',
    18000000.00,
    'Agreement',
    18000000.00,
    'AddedToCommonAssets',
    'PT-20260328-INK01'
)
ON CONFLICT (contribution_id) DO NOTHING;

INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
VALUES (
    'CTB-INKIND-001',
    'MEM_TRUONG_CHI',
    18000000.00
)
ON CONFLICT (contribution_id, member_id) DO NOTHING;

-- 6.2. Phiếu hiện vật 2: Nguyễn Thị Mai Anh cúng tiến 10 Mâm xôi gấc & gà trống thiến tế tổ (Đã thụ lộc/Consumed)
INSERT INTO contributions (
    contribution_id, event_id, payer_member_id, receiver_account_id, 
    contribution_date, contribution_type, total_monetary_value
)
VALUES (
    'CTB-INKIND-002',
    'EVT-GIO-TO-2026',
    'MEM_MAI_ANH',
    'ACC_TRUONG_CHI',
    CURRENT_TIMESTAMP - INTERVAL '6 hours',
    'InKind',
    5000000.00
)
ON CONFLICT (contribution_id) DO NOTHING;

INSERT INTO in_kind_contributions (
    contribution_id, item_name, quantity, unit, estimated_value, 
    conversion_method, converted_monetary_value, processing_status, receipt_number
)
VALUES (
    'CTB-INKIND-002',
    '10 Mâm xôi gấc & gà trống thiến tế tổ',
    10.0,
    'mâm',
    5000000.00,
    'BtcBenchmark',
    5000000.00,
    'Consumed',
    'PT-20260328-INK02'
)
ON CONFLICT (contribution_id) DO NOTHING;

INSERT INTO contribution_member_details (contribution_id, member_id, recorded_amount)
VALUES (
    'CTB-INKIND-002',
    'MEM_MAI_ANH',
    5000000.00
)
ON CONFLICT (contribution_id, member_id) DO NOTHING;

-- 7. Kho Bảo Vật & Hiện Vật Tích Lũy Từ Đường (Clan Assets)
INSERT INTO clan_assets (
    asset_id, clan_id, asset_name, quantity, unit, 
    assessed_value, storage_location, origin_contribution_id, created_at
)
VALUES
    (
        'AST-2026-001',
        'CLAN_DEFAULT',
        'Bộ đỉnh đồng tam sự chạm khắc song long',
        1,
        'bộ',
        18000000.00,
        'Gian thờ chính - Bàn thờ Cụ Khởi Tổ',
        'CTB-INKIND-001',
        CURRENT_TIMESTAMP - INTERVAL '5 hours'
    ),
    (
        'AST-2026-002',
        'CLAN_DEFAULT',
        'Hoành phi câu đối sơn son thếp vàng "Đức Lưu Quang"',
        1,
        'bộ',
        25000000.00,
        'Tiền sảnh Từ Đường Dòng Họ Nguyễn (Đại Tôn)',
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '100 days'
    ),
    (
        'AST-2026-003',
        'CLAN_DEFAULT',
        'Bát hương đồng ngũ sắc thời Nguyễn',
        3,
        'chiếc',
        12000000.00,
        'Hương án Từ Đường Dòng Họ Nguyễn',
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '150 days'
    )
ON CONFLICT (asset_id) DO NOTHING;
