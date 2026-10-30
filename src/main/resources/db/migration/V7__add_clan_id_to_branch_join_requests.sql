-- ==============================================================================
-- V7: Multi-Tenancy Support for Branch Join Requests
-- Module: Identity & Member Onboarding (US-17)
-- Engine: PostgreSQL 16+ (Fully Compatible with Neon Serverless Postgres)
-- ==============================================================================

-- 1. Add clan_id column to branch_join_requests table
ALTER TABLE branch_join_requests
    ADD COLUMN IF NOT EXISTS clan_id VARCHAR(20) REFERENCES clans(clan_id) ON DELETE RESTRICT;

-- 2. Populate clan_id from associated invitation_codes if records exist
UPDATE branch_join_requests bjr
SET clan_id = ic.clan_id
FROM invitation_codes ic
WHERE bjr.invite_code = ic.invite_code AND bjr.clan_id IS NULL;

-- 3. Fallback to CLAN_DEFAULT for any orphaned or legacy records
UPDATE branch_join_requests
SET clan_id = 'CLAN_DEFAULT'
WHERE clan_id IS NULL;

-- 4. Enforce NOT NULL constraint
ALTER TABLE branch_join_requests
    ALTER COLUMN clan_id SET NOT NULL;

-- 5. Create index for efficient tenant and approval status lookup
CREATE INDEX IF NOT EXISTS idx_join_request_clan_status ON branch_join_requests(clan_id, approval_status);

