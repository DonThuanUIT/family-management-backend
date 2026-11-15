-- US-43: Add optional registration_deadline and max_capacity columns to events
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS registration_deadline TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS max_capacity INTEGER;
