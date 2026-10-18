-- ==============================================================================
-- V3: Multi-Tenancy Architecture Support (Clans Management)
-- Module: Core Clan Management & Multi-Tenant Partitioning
-- Engine: PostgreSQL 16+ (Fully Compatible with Neon Serverless Postgres)
-- ==============================================================================

-- 1. Create CLANS table (Entity representing each independent family lineage / tenant)
CREATE TABLE IF NOT EXISTS clans (
    clan_id VARCHAR(20) PRIMARY KEY,
    clan_name VARCHAR(150) NOT NULL,
    ancestor_name VARCHAR(100),
    origin_location VARCHAR(255),
    established_year INT CHECK (established_year IS NULL OR established_year > 0),
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 2. Seed Default Clan for System Bootstrapping and Backward Compatibility
INSERT INTO clans (clan_id, clan_name, ancestor_name, origin_location, description)
VALUES (
    'CLAN_DEFAULT',
    'Dòng Họ Mẫu Mặc Định',
    'Tiên Tổ Khởi Lập',
    'Từ Đường Trung Tâm',
    'Dòng họ khởi tạo mặc định phục vụ backward compatibility cho các phân hệ'
)
ON CONFLICT (clan_id) DO NOTHING;

-- 3. Add clan_id Discriminator Column to Core Modules (Multi-Tenant Scoping)

-- 3.1. Genealogy Module: Family Branches
ALTER TABLE family_branches
    ADD COLUMN IF NOT EXISTS clan_id VARCHAR(20) REFERENCES clans(clan_id) ON DELETE RESTRICT;

UPDATE family_branches
SET clan_id = 'CLAN_DEFAULT'
WHERE clan_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_branch_clan ON family_branches(clan_id);

-- 3.2. Event Management Module: Events
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS clan_id VARCHAR(20) REFERENCES clans(clan_id) ON DELETE RESTRICT;

UPDATE events
SET clan_id = 'CLAN_DEFAULT'
WHERE clan_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_event_clan ON events(clan_id);

-- 3.3. Finance Module: Immutable Ledgers (Isolating Running Balance per Clan)
ALTER TABLE immutable_ledgers
    ADD COLUMN IF NOT EXISTS clan_id VARCHAR(20) REFERENCES clans(clan_id) ON DELETE RESTRICT;

UPDATE immutable_ledgers
SET clan_id = 'CLAN_DEFAULT'
WHERE clan_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_ledger_clan_created ON immutable_ledgers(clan_id, created_at);

-- 3.4. Identity & Invitation Module: Invitation Codes
ALTER TABLE invitation_codes
    ADD COLUMN IF NOT EXISTS clan_id VARCHAR(20) REFERENCES clans(clan_id) ON DELETE RESTRICT;

UPDATE invitation_codes
SET clan_id = 'CLAN_DEFAULT'
WHERE clan_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_invite_clan ON invitation_codes(clan_id);

-- 3.5. Asset Module: Clan Assets
ALTER TABLE clan_assets
    ADD COLUMN IF NOT EXISTS clan_id VARCHAR(20) REFERENCES clans(clan_id) ON DELETE RESTRICT;

UPDATE clan_assets
SET clan_id = 'CLAN_DEFAULT'
WHERE clan_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_asset_clan ON clan_assets(clan_id);

