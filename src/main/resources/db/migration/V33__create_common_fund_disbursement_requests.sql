-- ==============================================================================
-- V33: Create Common Fund Disbursement Requests & Extend Immutable Ledger (US-60)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS common_fund_disbursement_requests (
    request_id VARCHAR(20) PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(40) NOT NULL
        CHECK (category IN ('TempleMaintenance', 'ReliefSupport', 'Scholarship', 'Ceremony', 'Other')),
    amount DECIMAL(18,2) NOT NULL CHECK (amount > 0),
    beneficiary_name VARCHAR(255),
    beneficiary_member_id VARCHAR(20) REFERENCES members(member_id) ON DELETE SET NULL,
    purpose TEXT NOT NULL,
    attachment_url VARCHAR(500),
    proposed_by_account_id VARCHAR(20) NOT NULL REFERENCES user_accounts(account_id) ON DELETE RESTRICT,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'Pending'
        CHECK (approval_status IN ('Pending', 'Approved', 'Rejected')),
    reviewed_by_account_id VARCHAR(20) REFERENCES user_accounts(account_id) ON DELETE SET NULL,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_disbursement_clan_status_created
    ON common_fund_disbursement_requests(clan_id, approval_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_disbursement_clan_category
    ON common_fund_disbursement_requests(clan_id, category);

-- Drop existing CHECK constraint(s) on immutable_ledgers.transaction_type regardless of auto-generated name
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
    CHECK (transaction_type IN ('SurplusTransfer', 'AdjustmentTransaction', 'CommonFundDisbursement'));

ALTER TABLE immutable_ledgers
    ADD COLUMN IF NOT EXISTS disbursement_request_id VARCHAR(20) UNIQUE
        REFERENCES common_fund_disbursement_requests(request_id) ON DELETE SET NULL;
