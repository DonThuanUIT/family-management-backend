-- ==============================================================================
-- V17: Create payment_webhooks table for VietQR automated payment processing (US-53)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS payment_webhooks (
    webhook_id VARCHAR(50) PRIMARY KEY,
    reference_code VARCHAR(100) UNIQUE NOT NULL,
    gateway VARCHAR(50),
    amount NUMERIC(18, 2) NOT NULL,
    transfer_content VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    contribution_id VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_webhooks_reference ON payment_webhooks(reference_code);
