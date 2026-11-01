-- ==============================================================================
-- V10: Add Indexes, Constraints and Status for Family Event Registrations (US-06)
-- Module: Event Management
-- Engine: PostgreSQL 16+ (Fully Compatible with Neon Serverless Postgres)
-- ==============================================================================

-- 1. Add Status and Approval Workflow Columns to family_registrations
ALTER TABLE family_registrations
    ADD COLUMN IF NOT EXISTS status VARCHAR(25) NOT NULL DEFAULT 'Confirmed'
        CHECK (status IN ('Confirmed', 'PendingModification', 'PendingCancellation', 'Cancelled')),
    ADD COLUMN IF NOT EXISTS proposed_total_attendees INT CHECK (proposed_total_attendees IS NULL OR proposed_total_attendees > 0),
    ADD COLUMN IF NOT EXISTS proposed_vegetarian_meals INT CHECK (proposed_vegetarian_meals IS NULL OR proposed_vegetarian_meals >= 0),
    ADD COLUMN IF NOT EXISTS proposed_savory_meals INT CHECK (proposed_savory_meals IS NULL OR proposed_savory_meals >= 0),
    ADD COLUMN IF NOT EXISTS proposed_dietary_requirements VARCHAR(255),
    ADD COLUMN IF NOT EXISTS proposed_transportation_notes VARCHAR(255),
    ADD COLUMN IF NOT EXISTS modification_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reviewed_by_account_id VARCHAR(20) REFERENCES user_accounts(account_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

-- 2. Add Unique Constraint to Prevent Duplicate Active Registrations per Representative Member
CREATE UNIQUE INDEX IF NOT EXISTS uq_event_representative
    ON family_registrations(event_id, representative_member_id);

-- 3. Add Query Optimization Indexes
CREATE INDEX IF NOT EXISTS idx_family_reg_event
    ON family_registrations(event_id);

CREATE INDEX IF NOT EXISTS idx_family_reg_status
    ON family_registrations(status);

CREATE INDEX IF NOT EXISTS idx_reg_member_detail_member
    ON registration_member_details(member_id);
