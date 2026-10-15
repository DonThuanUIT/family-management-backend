-- ==============================================================================
-- Docker Postgres Entrypoint Initialization
-- Container: familyfund-postgres (pgvector/pgvector:pg16)
-- Responsibility: Enable superuser-level extensions for the database.
-- Note: Schema tables and initial data are fully managed by Flyway migrations
-- (located at src/main/resources/db/migration) to ensure 100% parity with Neon Cloud.
-- ==============================================================================

-- 1. Enable Required Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- 2. Verify Database Connection & Log Initial Status
DO $$
BEGIN
    RAISE NOTICE 'Database extensions initialized successfully. Flyway will execute migrations on application startup.';
END $$;
