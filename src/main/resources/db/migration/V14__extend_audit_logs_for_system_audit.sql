-- ==============================================================================
-- V13: Extend audit_logs table for System Audit Trail & JSONB Diff Tracking
-- Engine: PostgreSQL 16+ (Compatible with Neon Serverless Postgres)
-- ==============================================================================

-- 1. Drop existing inline CHECK constraints safely via pg_constraint
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT conname 
        FROM pg_constraint 
        WHERE conrelid = 'audit_logs'::regclass 
          AND contype = 'c' 
          AND (conname LIKE 'audit_logs_%_check' OR conname LIKE 'chk_audit_%')
    ) LOOP
        EXECUTE 'ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname);
    END LOOP;
END $$;

-- 2. Relax column NOT NULL constraints for general-purpose auditing
ALTER TABLE audit_logs ALTER COLUMN action_type TYPE VARCHAR(30);
ALTER TABLE audit_logs ALTER COLUMN target_member_id DROP NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN role_code DROP NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN scope_type DROP NOT NULL;
ALTER TABLE audit_logs ALTER COLUMN scope_id DROP NOT NULL;

-- 3. Add system audit tracking columns
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_type VARCHAR(30);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_id VARCHAR(30);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS old_value JSONB;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS new_value JSONB;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);

-- 4. Backfill existing US-03 role audit records
UPDATE audit_logs 
SET entity_type = 'ROLE', 
    entity_id = scope_id 
WHERE entity_type IS NULL;

ALTER TABLE audit_logs ALTER COLUMN entity_type SET NOT NULL;

-- 5. Add extended CHECK constraints
ALTER TABLE audit_logs ADD CONSTRAINT chk_audit_action_type 
    CHECK (action_type IN ('ASSIGN', 'REVOKE', 'APPROVE', 'REJECT', 'CREATE', 'UPDATE', 'DELETE', 'STATUS_CHANGE', 'ASSIGN_PERSONNEL'));

ALTER TABLE audit_logs ADD CONSTRAINT chk_audit_entity_type 
    CHECK (entity_type IN ('ROLE', 'JOIN_REQUEST', 'BRANCH', 'MEMBER', 'EVENT', 'EVENT_PERSONNEL'));

-- 6. Add composite performance indexes for query filtering
CREATE INDEX IF NOT EXISTS idx_audit_clan_actor_time ON audit_logs(clan_id, actor_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_clan_action_time ON audit_logs(clan_id, action_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_clan_entity ON audit_logs(clan_id, entity_type, entity_id);

-- 7. Immutability Trigger: block UPDATE operations on audit_logs
CREATE OR REPLACE FUNCTION audit_logs_block_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only and cannot be updated';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_logs_no_update ON audit_logs;
CREATE TRIGGER trg_audit_logs_no_update BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_block_update();
