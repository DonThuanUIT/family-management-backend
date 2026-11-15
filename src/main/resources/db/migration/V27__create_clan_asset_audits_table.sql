-- ==============================================================================
-- V16: Expand clan_assets and create clan_asset_audits for asset management (US-52)
-- ==============================================================================

ALTER TABLE clan_assets
    ADD COLUMN IF NOT EXISTS current_condition VARCHAR(50) DEFAULT 'Good',
    ADD COLUMN IF NOT EXISTS last_audited_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_audited_by VARCHAR(20);

CREATE TABLE IF NOT EXISTS clan_asset_audits (
    audit_id VARCHAR(20) PRIMARY KEY,
    asset_id VARCHAR(20) NOT NULL REFERENCES clan_assets(asset_id) ON DELETE CASCADE,
    audit_date TIMESTAMP WITH TIME ZONE NOT NULL,
    previous_quantity INT NOT NULL,
    actual_quantity INT NOT NULL,
    previous_condition VARCHAR(50),
    condition_status VARCHAR(50) NOT NULL,
    storage_location VARCHAR(255),
    auditor_account_id VARCHAR(20) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_clan_asset_audits_asset_id
    ON clan_asset_audits(asset_id);
