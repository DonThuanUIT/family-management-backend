-- ==============================================================================
-- V5: Enforce NOT NULL Constraints on Multi-Tenant Discriminator (clan_id)
-- Engine: PostgreSQL 16+ (Fully Compatible with Neon Serverless Postgres)
-- ==============================================================================

-- 1. Backfill any existing NULLs with default clan to prevent constraint violation
UPDATE family_branches SET clan_id = 'CLAN_DEFAULT' WHERE clan_id IS NULL;
UPDATE events SET clan_id = 'CLAN_DEFAULT' WHERE clan_id IS NULL;
UPDATE immutable_ledgers SET clan_id = 'CLAN_DEFAULT' WHERE clan_id IS NULL;
UPDATE invitation_codes SET clan_id = 'CLAN_DEFAULT' WHERE clan_id IS NULL;
UPDATE clan_assets SET clan_id = 'CLAN_DEFAULT' WHERE clan_id IS NULL;

-- 2. Enforce NOT NULL constraints on all multi-tenant core entities
ALTER TABLE family_branches ALTER COLUMN clan_id SET NOT NULL;
ALTER TABLE events ALTER COLUMN clan_id SET NOT NULL;
ALTER TABLE immutable_ledgers ALTER COLUMN clan_id SET NOT NULL;
ALTER TABLE invitation_codes ALTER COLUMN clan_id SET NOT NULL;
ALTER TABLE clan_assets ALTER COLUMN clan_id SET NOT NULL;
