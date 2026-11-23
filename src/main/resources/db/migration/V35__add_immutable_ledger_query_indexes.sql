-- =================================================================================
-- Migration: V35__add_immutable_ledger_query_indexes.sql
-- Purpose: Add read-optimization indexes on immutable_ledgers for US-62 (GET /api/ledger)
-- =================================================================================

CREATE INDEX IF NOT EXISTS idx_ledger_clan_original_tx ON immutable_ledgers(clan_id, original_transaction_id) WHERE original_transaction_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ledger_clan_type_created ON immutable_ledgers(clan_id, transaction_type, created_at DESC, transaction_id DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_clan_event_created ON immutable_ledgers(clan_id, event_id, created_at DESC) WHERE event_id IS NOT NULL;
