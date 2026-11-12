-- US-42: Add shuttle_seats column to family_registrations for real-time transport capacity aggregation
ALTER TABLE family_registrations
    ADD COLUMN IF NOT EXISTS shuttle_seats INTEGER NOT NULL DEFAULT 0;
