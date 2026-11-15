-- ==============================================================================
-- V18: Multi-Tenant Clan Banking Configuration for Dynamic VietQR Payments
-- Engine: PostgreSQL 16+
-- ==============================================================================

ALTER TABLE clans
    ADD COLUMN IF NOT EXISTS bank_bin VARCHAR(10),
    ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS bank_account_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS qr_template VARCHAR(20) DEFAULT 'compact2';

-- Seed / Update initial banking config for default clan
UPDATE clans
SET bank_bin = '970422',
    bank_account_number = '0987654321',
    bank_account_name = 'QUY DONG HO MAC DINH',
    qr_template = 'compact2'
WHERE clan_id = 'CLAN_DEFAULT' AND bank_account_number IS NULL;
