-- ==============================================================================
-- Flyway Migration V17: Bổ sung trường địa chỉ liên lạc cho tài khoản thành viên (US-22)
-- ==============================================================================

ALTER TABLE user_accounts
    ADD COLUMN IF NOT EXISTS address VARCHAR(255);

COMMENT ON COLUMN user_accounts.address IS 'Địa chỉ liên lạc hoặc thường trú của thành viên để nhận thông báo việc họ (US-22)';
