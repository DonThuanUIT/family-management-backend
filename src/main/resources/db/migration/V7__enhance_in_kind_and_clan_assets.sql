-- ==============================================================================
-- V7: Enhance In-Kind Contributions, Clan Assets & Event Item Benchmarks (US-10)
-- ==============================================================================

-- 1. Bổ sung mã phiếu thu điện tử duy nhất cho hiện vật cúng tiến (US-10, Task #72, #74)
ALTER TABLE in_kind_contributions 
    ADD COLUMN IF NOT EXISTS receipt_number VARCHAR(30);

CREATE UNIQUE INDEX IF NOT EXISTS uq_in_kind_receipt_number 
    ON in_kind_contributions(receipt_number);

-- 2. Bổ sung thời gian tạo và chỉ mục tìm kiếm cho bảng tài sản dòng họ (Task #73, #74)
ALTER TABLE clan_assets 
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_clan_assets_search 
    ON clan_assets(clan_id, asset_name);

-- 3. Bảng định mức giá hiện vật cúng tiến do Ban tổ chức ban hành (Task #72)
CREATE TABLE IF NOT EXISTS event_item_benchmarks (
    benchmark_id VARCHAR(20) PRIMARY KEY,
    event_id VARCHAR(20) NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    clan_id VARCHAR(20) NOT NULL REFERENCES clans(clan_id) ON DELETE RESTRICT,
    item_name VARCHAR(100) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    unit_price DECIMAL(18,2) NOT NULL CHECK (unit_price >= 0),
    note VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_benchmarks_event ON event_item_benchmarks(event_id);
CREATE INDEX IF NOT EXISTS idx_benchmarks_clan ON event_item_benchmarks(clan_id);
