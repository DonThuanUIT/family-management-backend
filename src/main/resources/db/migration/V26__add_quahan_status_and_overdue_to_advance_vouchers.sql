-- ==============================================================================
-- V26: Bổ sung trạng thái QuaHan và cột overdue_at cho advance_vouchers (US-48)
-- Engine: PostgreSQL 16+ (Fully Compatible with Neon Serverless Postgres)
-- ==============================================================================

-- 1. Bổ sung cột lưu vết thời điểm hệ thống chuyển trạng thái Quá hạn và gửi cảnh báo
ALTER TABLE advance_vouchers ADD COLUMN IF NOT EXISTS overdue_at TIMESTAMPTZ;

-- 2. Cập nhật ràng buộc CHECK trên cột status để chấp nhận trạng thái QuaHan / Overdue
ALTER TABLE advance_vouchers DROP CONSTRAINT IF EXISTS chk_advance_vouchers_status;

ALTER TABLE advance_vouchers ADD CONSTRAINT chk_advance_vouchers_status
    CHECK (status IN (
        'ChoGiaoTien', 'ChuaTatToan', 'DaTatToan', 'DaHuy', 'QuaHan',
        'PendingDisbursement', 'Disbursed', 'Settled', 'Cancelled', 'Overdue'
    ));

-- 3. Tạo index tối ưu hóa cho background job quét các phiếu đang chưa quyết toán quá hạn
CREATE INDEX IF NOT EXISTS idx_advance_vouchers_overdue_scan
    ON advance_vouchers(settlement_deadline)
    WHERE status = 'ChuaTatToan';
