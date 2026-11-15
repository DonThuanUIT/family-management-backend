-- ==============================================================================
-- V15: Expand cash_contributions table for bank transfer reconciliation (US-50)
-- ==============================================================================

ALTER TABLE cash_contributions
    ADD COLUMN IF NOT EXISTS bank_transaction_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS transfer_proof_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS is_reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS reconciled_by VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_cash_contributions_reconciliation
    ON cash_contributions (payment_method, is_reconciled);
