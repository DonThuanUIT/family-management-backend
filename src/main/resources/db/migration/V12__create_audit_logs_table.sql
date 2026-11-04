-- ==============================================================================
-- V11: Audit Logs Schema for Dual-RBAC Role Assignment & Revocation (US-03)
-- Engine: PostgreSQL 16+ (Compatible with Neon Serverless Postgres)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE,
    actor_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    target_member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    target_account_id VARCHAR(20) REFERENCES user_accounts(account_id) ON DELETE SET NULL,
    action_type VARCHAR(20) NOT NULL CHECK (action_type IN ('ASSIGN', 'REVOKE')),
    role_code VARCHAR(30) NOT NULL CHECK (role_code IN ('ROLE_BRANCH_HEAD', 'HeadOfOrganizer', 'OrganizerMember', 'EventTreasurer')),
    scope_type VARCHAR(20) NOT NULL CHECK (scope_type IN ('BRANCH', 'EVENT')),
    scope_id VARCHAR(20) NOT NULL,
    scope_name VARCHAR(150),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_clan_created_at ON audit_logs(clan_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_target_member ON audit_logs(target_member_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_scope ON audit_logs(scope_type, scope_id);
