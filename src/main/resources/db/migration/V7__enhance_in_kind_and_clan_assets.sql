-- ==============================================================================
-- V7: Enhance In-Kind Contributions (US-10)
-- ==============================================================================

-- 1. Bổ sung mã phiếu thu điện tử duy nhất cho hiện vật cúng tiến (US-10, Task #72, #74)
ALTER TABLE in_kind_contributions 
    ADD COLUMN IF NOT EXISTS receipt_number VARCHAR(30);

CREATE UNIQUE INDEX IF NOT EXISTS uq_in_kind_receipt_number 
    ON in_kind_contributions(receipt_number);
