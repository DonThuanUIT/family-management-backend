-- Flyway migration V15: Create member_privacy_settings table for US-25 (Hybrid Privacy)
CREATE TABLE IF NOT EXISTS member_privacy_settings (
    member_id VARCHAR(20) PRIMARY KEY REFERENCES members(member_id) ON DELETE CASCADE,
    share_date_of_birth BOOLEAN NOT NULL DEFAULT FALSE,
    share_phone_number BOOLEAN NOT NULL DEFAULT FALSE,
    share_email BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_member_privacy_settings_member_id ON member_privacy_settings(member_id);
