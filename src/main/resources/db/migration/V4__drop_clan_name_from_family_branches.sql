-- ==============================================================================
-- V4: Normalize Data - Remove redundant clan_name from family_branches
-- Module: Genealogy
-- ==============================================================================

ALTER TABLE family_branches DROP COLUMN IF EXISTS clan_name;
