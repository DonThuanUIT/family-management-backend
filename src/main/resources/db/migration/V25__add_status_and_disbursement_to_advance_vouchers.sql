-- ==============================================================================
-- V25: Bổ sung status, thông tin giao tiền và kiểm soát đa dòng họ cho advance_vouchers (US-46)
-- Engine: PostgreSQL 16+ (Fully Compatible with Neon Serverless Postgres)
-- ==============================================================================

-- 1. Bổ sung clan_id để quản lý đa dòng họ (multi-tenancy) trực tiếp trên phiếu tạm ứng
ALTER TABLE advance_vouchers ADD COLUMN IF NOT EXISTS clan_id VARCHAR(20) REFERENCES clans(clan_id) ON DELETE CASCADE;

-- Backfill clan_id từ event_tasks và events cho các bản ghi đã có
UPDATE advance_vouchers av
SET clan_id = e.clan_id
FROM event_tasks et
JOIN events e ON et.event_id = e.event_id
WHERE av.task_id = et.task_id AND av.clan_id IS NULL;

-- 2. Bổ sung trạng thái phiếu tạm ứng (US-46):
--    - ChoGiaoTien: BTC đã lập phiếu, chờ Thủ quỹ giao tiền
--    - ChuaTatToan: Thủ quỹ đã giao tiền, người nhận đang cầm tiền đi chợ/thực hiện việc (chờ quyết toán)
--    - DaTatToan: Đã đối soát và hoàn tất quyết toán chứng từ
--    - DaHuy: Phiếu đã bị hủy trước khi giao tiền
ALTER TABLE advance_vouchers ADD COLUMN IF NOT EXISTS status VARCHAR(25) NOT NULL DEFAULT 'ChoGiaoTien';

-- 3. Bổ sung thông tin xác nhận giao tiền của Thủ quỹ
ALTER TABLE advance_vouchers ADD COLUMN IF NOT EXISTS treasurer_account_id VARCHAR(20) REFERENCES user_accounts(account_id) ON DELETE RESTRICT;
ALTER TABLE advance_vouchers ADD COLUMN IF NOT EXISTS disbursed_at TIMESTAMPTZ;
ALTER TABLE advance_vouchers ADD COLUMN IF NOT EXISTS disbursement_notes TEXT;

-- 4. Bổ sung audit timestamp
ALTER TABLE advance_vouchers ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE advance_vouchers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 5. Ràng buộc CHECK cho status (chấp nhận cả định dạng tiếng Việt chuẩn và tương đương)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_advance_vouchers_status'
    ) THEN
        ALTER TABLE advance_vouchers ADD CONSTRAINT chk_advance_vouchers_status
            CHECK (status IN ('ChoGiaoTien', 'ChuaTatToan', 'DaTatToan', 'DaHuy', 'PendingDisbursement', 'Disbursed', 'Settled', 'Cancelled'));
    END IF;
END $$;

-- 6. Indexes tối ưu truy vấn tra cứu theo task, người nhận, clan và hạn nộp chứng từ
CREATE INDEX IF NOT EXISTS idx_advance_vouchers_task_id ON advance_vouchers(task_id);
CREATE INDEX IF NOT EXISTS idx_advance_vouchers_recipient ON advance_vouchers(recipient_member_id);
CREATE INDEX IF NOT EXISTS idx_advance_vouchers_clan_status ON advance_vouchers(clan_id, status);
CREATE INDEX IF NOT EXISTS idx_advance_vouchers_deadline ON advance_vouchers(settlement_deadline);
