-- ==============================================================================
-- V23: Unified notification inbox (US-39) & Firebase Cloud Messaging device tokens
-- Engine: PostgreSQL 16+ (Fully Compatible with Neon Serverless Postgres)
-- ==============================================================================

-- 1. NOTIFICATION: hộp thư thông báo cá nhân, phân loại theo 3 nhóm nghiệp vụ.
--    `recipient_account_id` NOT NULL: mỗi thông báo thuộc về đúng một tài khoản.
CREATE TABLE IF NOT EXISTS notifications (
    notification_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE,
    recipient_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL CHECK (category IN ('Event', 'Task', 'Finance')),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    -- Liên kết mềm tới đối tượng nghiệp vụ (EVENT/TASK/CONTRIBUTION/ROLE...)
    reference_type VARCHAR(30),
    reference_id VARCHAR(20),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. NOTIFICATION_DEVICE_TOKENS: token FCM của thiết bị di động/web của từng tài khoản.
--    `fcm_token` UNIQUE để một thiết bị đăng nhập nhiều tài khoản vẫn chỉ có một bản ghi
--    (đăng ký lại sẽ chuyển quyền sở hữu token sang tài khoản đăng ký sau).
CREATE TABLE IF NOT EXISTS notification_device_tokens (
    token_id VARCHAR(20) PRIMARY KEY,
    account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE CASCADE,
    fcm_token VARCHAR(255) NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL DEFAULT 'Android' CHECK (platform IN ('Android', 'IOS', 'Web')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ
);

-- 3. Indexes phục vụ hộp thư (lọc theo tài khoản, nhóm, trạng thái đã đọc) và gửi push
CREATE INDEX IF NOT EXISTS idx_notification_recipient_created
    ON notifications(recipient_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_recipient_category
    ON notifications(recipient_account_id, category);
CREATE INDEX IF NOT EXISTS idx_notification_clan
    ON notifications(clan_id);
CREATE INDEX IF NOT EXISTS idx_notification_unread
    ON notifications(recipient_account_id) WHERE is_read = FALSE;
CREATE INDEX IF NOT EXISTS idx_device_token_account_active
    ON notification_device_tokens(account_id) WHERE is_active = TRUE;
