-- Local storage was removed after hosted Storage verification.
-- Never strand an existing local object or edit the already-applied V19 migration.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM stored_files WHERE provider = 'LOCAL' AND state <> 'DELETED') THEN
        RAISE EXCEPTION 'Migrate active LOCAL objects to Supabase before applying V20';
    END IF;
END;
$$;

ALTER TABLE stored_files DROP CONSTRAINT IF EXISTS stored_files_provider_check;
ALTER TABLE stored_files ADD CONSTRAINT stored_files_cloud_provider_check
    CHECK (provider = 'SUPABASE' OR (provider = 'LOCAL' AND state = 'DELETED'));
