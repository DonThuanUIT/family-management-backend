-- ==============================================================================
-- V36: Create ledger_annual_audits table & add OpeningBalanceCarryForward type
-- ==============================================================================

-- 1. Drop existing CHECK constraint(s) on immutable_ledgers.transaction_type
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'immutable_ledgers'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%transaction_type%'
    LOOP
        EXECUTE format('ALTER TABLE immutable_ledgers DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE immutable_ledgers
    ADD CONSTRAINT chk_immutable_ledgers_transaction_type
    CHECK (transaction_type IN (
        'SurplusTransfer',
        'AdjustmentTransaction',
        'CommonFundDisbursement',
        'OpeningBalanceCarryForward'
    ));

-- 2. Create ledger_annual_audits table to track annual reconciliation & opening balance rollovers
CREATE TABLE IF NOT EXISTS ledger_annual_audits (
    audit_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE,
    audit_year INTEGER NOT NULL CHECK (audit_year BETWEEN 2000 AND 2100),
    next_period_year INTEGER NOT NULL CHECK (next_period_year = audit_year + 1),
    audit_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    system_balance DECIMAL(18,2) NOT NULL,
    actual_balance DECIMAL(18,2) NOT NULL CHECK (actual_balance >= 0),
    discrepancy_amount DECIMAL(18,2) NOT NULL,
    audit_status VARCHAR(25) NOT NULL
        CHECK (audit_status IN ('Matched', 'DiscrepancyDetected')),
    reference_transaction_id VARCHAR(20)
        REFERENCES immutable_ledgers(transaction_id) ON DELETE RESTRICT,
    adjustment_request_id VARCHAR(20) UNIQUE
        REFERENCES ledger_adjustment_requests(request_id) ON DELETE RESTRICT,
    closing_transaction_id VARCHAR(20) UNIQUE
        REFERENCES immutable_ledgers(transaction_id) ON DELETE RESTRICT,
    opening_balance_next_period DECIMAL(18,2),
    auditor_account_id VARCHAR(20) NOT NULL
        REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    notes TEXT,
    evidence_attachment_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_annual_audit_status_consistency CHECK (
        (audit_status = 'Matched'
            AND discrepancy_amount = 0
            AND opening_balance_next_period IS NOT NULL
            AND closing_transaction_id IS NOT NULL
            AND adjustment_request_id IS NULL)
        OR
        (audit_status = 'DiscrepancyDetected'
            AND discrepancy_amount <> 0
            AND opening_balance_next_period IS NULL
            AND closing_transaction_id IS NULL
            AND adjustment_request_id IS NOT NULL)
    )
);

-- Enforce at most one Matched annual audit per clan and fiscal year
CREATE UNIQUE INDEX IF NOT EXISTS uq_annual_audit_clan_year_matched
    ON ledger_annual_audits(clan_id, audit_year)
    WHERE audit_status = 'Matched';

CREATE INDEX IF NOT EXISTS idx_annual_audit_clan_year_created
    ON ledger_annual_audits(clan_id, audit_year DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_annual_audit_clan_status
    ON ledger_annual_audits(clan_id, audit_status, created_at DESC);
