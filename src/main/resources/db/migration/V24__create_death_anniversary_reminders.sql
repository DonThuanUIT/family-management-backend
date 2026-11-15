-- ==============================================================================
-- V24: Nhắc giỗ Âm lịch hằng ngày (US-40)
-- Engine: PostgreSQL 16+ (Fully Compatible with Neon Serverless Postgres)
-- ==============================================================================

-- 1. DEATH_ANNIVERSARY_REMINDERS: vết đã gửi nhắc giỗ cho một thành viên trong một năm âm lịch.
--    Khóa idempotency (member_id, lunar_year, milestone_days): mỗi mốc cấu hình chỉ gửi đúng một lần
--    cho mỗi người trong mỗi năm, kể cả khi cron job chạy nhiều lần trong ngày hoặc máy chủ phục hồi.
CREATE TABLE IF NOT EXISTS death_anniversary_reminders (
    reminder_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE CASCADE,
    -- Năm Dương lịch của lần giỗ (giỗ diễn ra trong năm âm lịch tương ứng)
    anniversary_year INT NOT NULL,
    -- Năm âm lịch dùng để đối chiếu ngày giỗ (thường bằng năm dương lịch)
    lunar_year INT NOT NULL,
    -- Mốc nhắc: số ngày trước ngày giỗ (14 hoặc 3, xem app.death-anniversary.milestones)
    milestone_days INT NOT NULL CHECK (milestone_days >= 0),
    -- Ngày giỗ Dương lịch quy đổi (ngày âm 15/02 năm nay rơi vào ngày dương nào)
    anniversary_date DATE NOT NULL,
    -- Ngày Dương lịch mà cron job đã phát hiện và gửi nhắc
    trigger_date DATE NOT NULL,
    -- Tổng số người nhận đã ghi vào hộp thư (0 nếu chưa ai có tài khoản)
    recipient_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_death_anniversary_reminder UNIQUE (member_id, lunar_year, milestone_days)
);

-- 2. Indexes: cron quét theo ngày giỗ / clan, và tra cứu vết đã gửi
CREATE INDEX IF NOT EXISTS idx_dar_member_lunar_year
    ON death_anniversary_reminders(member_id, lunar_year);
CREATE INDEX IF NOT EXISTS idx_dar_clan_anniversary
    ON death_anniversary_reminders(clan_id, anniversary_date);
CREATE INDEX IF NOT EXISTS idx_dar_trigger_date
    ON death_anniversary_reminders(trigger_date);

-- 3. Index hỗ trợ truy vấn thành viên đã mất có ngày giỗ âm lịch (cron quét mỗi ngày)
CREATE INDEX IF NOT EXISTS idx_members_status_death_lunar
    ON members(status, death_date_lunar) WHERE death_date_lunar IS NOT NULL;
