-- ==============================================================================
-- V34: Enforce Multi-Layer Immutability & SHA-256 Hash Chaining on immutable_ledgers (NFR-01 / US-61)
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Add explicit previous_hash link column (NO DEFAULT so INSERT trigger enforces/populates chain)
ALTER TABLE immutable_ledgers
    ADD COLUMN IF NOT EXISTS previous_hash VARCHAR(64);

-- 2. Backfill any pre-existing rows per clan chronologically before NOT NULL & triggers
DO $$
DECLARE
    v_clan RECORD;
    v_row RECORD;
    v_prev_hash VARCHAR(64);
BEGIN
    FOR v_clan IN
        SELECT DISTINCT clan_id FROM immutable_ledgers
    LOOP
        v_prev_hash := 'GENESIS';
        FOR v_row IN
            SELECT transaction_id, record_hash
            FROM immutable_ledgers
            WHERE clan_id = v_clan.clan_id
            ORDER BY created_at ASC, transaction_id ASC
        LOOP
            UPDATE immutable_ledgers
            SET previous_hash = v_prev_hash
            WHERE transaction_id = v_row.transaction_id
              AND previous_hash IS NULL;

            v_prev_hash := v_row.record_hash;
        END LOOP;
    END LOOP;
END $$;

ALTER TABLE immutable_ledgers
    ALTER COLUMN previous_hash SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ledger_clan_created_tx
    ON immutable_ledgers(clan_id, created_at, transaction_id);

-- 3. Enforce strict lowercase 64-hex SHA-256 format constraints
ALTER TABLE immutable_ledgers
    DROP CONSTRAINT IF EXISTS chk_immutable_ledgers_record_hash_sha256;

ALTER TABLE immutable_ledgers
    ADD CONSTRAINT chk_immutable_ledgers_record_hash_sha256
    CHECK (record_hash ~ '^[0-9a-f]{64}$');

ALTER TABLE immutable_ledgers
    DROP CONSTRAINT IF EXISTS chk_immutable_ledgers_previous_hash_sha256;

ALTER TABLE immutable_ledgers
    ADD CONSTRAINT chk_immutable_ledgers_previous_hash_sha256
    CHECK (previous_hash = 'GENESIS' OR previous_hash ~ '^[0-9a-f]{64}$');

-- 4. Tighten nullable reference FKs from ON DELETE SET NULL -> ON DELETE RESTRICT
--    (prevents implicit cascading UPDATE on referenced ledger columns if parent row is deleted)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
        WHERE c.conrelid = 'immutable_ledgers'::regclass
          AND c.contype = 'f'
          AND a.attname IN ('event_id', 'adjustment_request_id', 'disbursement_request_id')
    LOOP
        EXECUTE format('ALTER TABLE immutable_ledgers DROP CONSTRAINT IF EXISTS %I', r.conname);
    END LOOP;
END $$;

ALTER TABLE immutable_ledgers
    ADD CONSTRAINT immutable_ledgers_event_id_fkey
    FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE RESTRICT;

ALTER TABLE immutable_ledgers
    ADD CONSTRAINT immutable_ledgers_adjustment_request_id_fkey
    FOREIGN KEY (adjustment_request_id) REFERENCES ledger_adjustment_requests(request_id) ON DELETE RESTRICT;

ALTER TABLE immutable_ledgers
    ADD CONSTRAINT immutable_ledgers_disbursement_request_id_fkey
    FOREIGN KEY (disbursement_request_id) REFERENCES common_fund_disbursement_requests(request_id) ON DELETE RESTRICT;

-- 5. BEFORE INSERT trigger: enforce advisory lock, monotonic created_at, previous_hash chain link,
--    and fallback pgcrypto SHA-256 hash computation for direct SQL inserts
CREATE OR REPLACE FUNCTION immutable_ledgers_enforce_chain_on_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_latest_hash VARCHAR(64);
    v_latest_created_at TIMESTAMPTZ;
    v_expected_prev_hash VARCHAR(64);
    v_ts_utc TIMESTAMP;
    v_micros INTEGER;
    v_iso_ts TEXT;
    v_canonical_payload TEXT;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('immutable_ledger:' || NEW.clan_id));

    NEW.created_at := date_trunc('microsecond', COALESCE(NEW.created_at, CURRENT_TIMESTAMP));

    SELECT record_hash, created_at
    INTO v_latest_hash, v_latest_created_at
    FROM immutable_ledgers
    WHERE clan_id = NEW.clan_id
    ORDER BY created_at DESC, transaction_id DESC
    LIMIT 1;

    v_expected_prev_hash := COALESCE(v_latest_hash, 'GENESIS');

    IF NEW.previous_hash IS NULL OR btrim(NEW.previous_hash) = '' THEN
        NEW.previous_hash := v_expected_prev_hash;
    ELSE
        NEW.previous_hash := btrim(NEW.previous_hash);
        IF NEW.previous_hash <> v_expected_prev_hash THEN
            RAISE EXCEPTION USING
                ERRCODE = '42501',
                MESSAGE = format(
                    'SECURITY VIOLATION [NFR-01]: Broken SHA-256 hash chain on immutable_ledgers for clan %s (expected previous_hash=%s, got=%s).',
                    NEW.clan_id,
                    v_expected_prev_hash,
                    NEW.previous_hash
                );
        END IF;
    END IF;

    IF v_latest_created_at IS NOT NULL AND NEW.created_at < v_latest_created_at THEN
        RAISE EXCEPTION USING
            ERRCODE = '42501',
            MESSAGE = format(
                'SECURITY VIOLATION [NFR-01]: Backdated insert rejected on immutable_ledgers for clan %s (created_at=%s < latest=%s).',
                NEW.clan_id,
                NEW.created_at,
                v_latest_created_at
            );
    END IF;

    IF NEW.record_hash IS NULL OR btrim(NEW.record_hash) = '' THEN
        v_ts_utc := NEW.created_at AT TIME ZONE 'UTC';
        v_micros := to_char(v_ts_utc, 'US')::integer;

        IF v_micros = 0 THEN
            v_iso_ts := to_char(v_ts_utc, 'YYYY-MM-DD"T"HH24:MI:SS') || 'Z';
        ELSIF v_micros % 1000 = 0 THEN
            v_iso_ts := to_char(v_ts_utc, 'YYYY-MM-DD"T"HH24:MI:SS') || '.' || lpad((v_micros / 1000)::text, 3, '0') || 'Z';
        ELSE
            v_iso_ts := to_char(v_ts_utc, 'YYYY-MM-DD"T"HH24:MI:SS') || '.' || lpad(v_micros::text, 6, '0') || 'Z';
        END IF;

        v_canonical_payload := array_to_string(ARRAY[
            NEW.previous_hash,
            NEW.transaction_id,
            NEW.clan_id,
            COALESCE(NEW.event_id, ''),
            COALESCE(NEW.adjustment_request_id, ''),
            COALESCE(NEW.disbursement_request_id, ''),
            COALESCE(NEW.original_transaction_id, ''),
            NEW.transaction_type,
            trim(to_char(round(NEW.amount, 2), 'FM9999999999999990.00')),
            trim(to_char(round(NEW.running_balance, 2), 'FM9999999999999990.00')),
            COALESCE(NEW.description, ''),
            NEW.created_by_account_id,
            NEW.approved_by_account_id,
            v_iso_ts
        ], '|');

        NEW.record_hash := encode(public.digest(convert_to(v_canonical_payload, 'UTF8'), 'sha256'), 'hex');
    ELSE
        NEW.record_hash := btrim(NEW.record_hash);
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_immutable_ledgers_chain_guard ON immutable_ledgers;
CREATE TRIGGER trg_immutable_ledgers_chain_guard
    BEFORE INSERT ON immutable_ledgers
    FOR EACH ROW
    EXECUTE FUNCTION immutable_ledgers_enforce_chain_on_insert();

-- 6. Mutation-blocking triggers: strictly forbid UPDATE, DELETE, and TRUNCATE at both statement and row levels
CREATE OR REPLACE FUNCTION immutable_ledgers_block_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '42501',
        MESSAGE = 'SECURITY VIOLATION [NFR-01]: immutable_ledgers is append-only; ' || TG_OP || ' operations are strictly prohibited.';
END;
$$;

DROP TRIGGER IF EXISTS trg_immutable_ledgers_no_mutation_stmt ON immutable_ledgers;
CREATE TRIGGER trg_immutable_ledgers_no_mutation_stmt
    BEFORE UPDATE OR DELETE OR TRUNCATE ON immutable_ledgers
    FOR EACH STATEMENT
    EXECUTE FUNCTION immutable_ledgers_block_mutation();

DROP TRIGGER IF EXISTS trg_immutable_ledgers_no_mutation_row ON immutable_ledgers;
CREATE TRIGGER trg_immutable_ledgers_no_mutation_row
    BEFORE UPDATE OR DELETE ON immutable_ledgers
    FOR EACH ROW
    EXECUTE FUNCTION immutable_ledgers_block_mutation();
