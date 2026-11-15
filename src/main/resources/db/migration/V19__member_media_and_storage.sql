-- US-32: private member media. File metadata survives cleanup failures.
CREATE TABLE stored_files (
    id UUID PRIMARY KEY,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE RESTRICT,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('AVATAR','ATTACHMENT')),
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('LOCAL','SUPABASE')),
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    checksum_sha256 VARCHAR(64) NOT NULL,
    created_by VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING','READY','DELETE_PENDING','DELETED')),
    cleanup_after TIMESTAMPTZ NOT NULL DEFAULT now() + interval '24 hours',
    delete_attempts INTEGER NOT NULL DEFAULT 0,
    last_delete_error VARCHAR(100),
    UNIQUE(provider,bucket,object_key)
);
CREATE INDEX idx_stored_files_cleanup ON stored_files(state,cleanup_after);
CREATE INDEX idx_stored_files_member ON stored_files(clan_id,member_id);

ALTER TABLE members
    ADD COLUMN avatar_file_id UUID REFERENCES stored_files(id) ON DELETE RESTRICT,
    ADD COLUMN avatar_url TEXT,
    ADD COLUMN biography JSONB,
    ADD COLUMN biography_visibility VARCHAR(20) NOT NULL DEFAULT 'RESTRICTED'
        CHECK (biography_visibility IN ('CLAN','BRANCH','RESTRICTED')),
    ADD COLUMN media_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN media_updated_at TIMESTAMPTZ,
    ADD COLUMN media_updated_by VARCHAR(20);

CREATE TABLE member_attachments (
    id UUID PRIMARY KEY,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    file_id UUID NOT NULL UNIQUE REFERENCES stored_files(id) ON DELETE RESTRICT,
    title VARCHAR(150) NOT NULL CHECK (length(trim(title)) > 0),
    description VARCHAR(1000),
    visibility VARCHAR(20) NOT NULL DEFAULT 'RESTRICTED' CHECK (visibility IN ('CLAN','BRANCH','RESTRICTED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(20) NOT NULL,
    updated_by VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_member_attachments_page ON member_attachments(member_id,deleted_at,created_at DESC,id DESC);

-- A singleton row serializes quota reservations across all tenants and instances.
CREATE TABLE media_quota_lock (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO media_quota_lock VALUES (1);

CREATE TABLE media_upload_requests (
    id UUID PRIMARY KEY,
    caller_account_id VARCHAR(20) NOT NULL,
    member_id VARCHAR(20) NOT NULL REFERENCES members(member_id) ON DELETE RESTRICT,
    idempotency_key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    file_id UUID REFERENCES stored_files(id) ON DELETE SET NULL,
    attachment_id UUID REFERENCES member_attachments(id) ON DELETE RESTRICT,
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING','COMPLETE','FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT now() + interval '24 hours',
    UNIQUE(caller_account_id,member_id,idempotency_key)
);
CREATE INDEX idx_media_upload_account_time ON media_upload_requests(caller_account_id,created_at);

-- Prevent cross-profile references even if a future caller bypasses the service.
CREATE FUNCTION check_member_media_file() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'members' THEN
        IF NEW.avatar_file_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM stored_files f JOIN family_branches b ON b.branch_id = NEW.branch_id
            WHERE f.id=NEW.avatar_file_id AND f.member_id=NEW.member_id
                AND f.clan_id=b.clan_id AND f.purpose='AVATAR' AND f.state='READY'
        ) THEN RAISE EXCEPTION 'Invalid avatar file reference'; END IF;
    ELSE
        IF NOT EXISTS (SELECT 1 FROM stored_files f WHERE f.id=NEW.file_id
            AND f.member_id=NEW.member_id AND f.purpose='ATTACHMENT')
        THEN RAISE EXCEPTION 'Invalid attachment file reference'; END IF;
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_check_member_avatar BEFORE INSERT OR UPDATE OF avatar_file_id ON members
    FOR EACH ROW EXECUTE FUNCTION check_member_media_file();
CREATE TRIGGER trg_check_attachment_file BEFORE INSERT OR UPDATE OF file_id,member_id ON member_attachments
    FOR EACH ROW EXECUTE FUNCTION check_member_media_file();
