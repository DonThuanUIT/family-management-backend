-- ==============================================================================
-- V13: Enable unaccent extension and add performance indexes for member search (US-30)
-- Engine: PostgreSQL 16+ (Fully Compatible with Neon Serverless Postgres)
-- ==============================================================================

-- 1. Enable PostgreSQL unaccent extension for accent-insensitive search
CREATE EXTENSION IF NOT EXISTS unaccent;

-- 2. Indexes for efficient lookup of members and branch hierarchy
CREATE INDEX IF NOT EXISTS idx_members_branch_id ON members(branch_id);
CREATE INDEX IF NOT EXISTS idx_members_generation ON members(generation);
