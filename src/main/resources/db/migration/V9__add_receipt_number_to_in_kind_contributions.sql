-- ==============================================================================
-- V9: Ensure receipt_number on in_kind_contributions (US-10)
-- ==============================================================================

ALTER TABLE in_kind_contributions 
    ADD COLUMN IF NOT EXISTS receipt_number VARCHAR(30);

CREATE UNIQUE INDEX IF NOT EXISTS uq_in_kind_receipt_number 
    ON in_kind_contributions(receipt_number);
