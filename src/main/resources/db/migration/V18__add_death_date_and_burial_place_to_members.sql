-- V18: Bổ sung ngày mất dương lịch và địa điểm an táng cho thành viên (US-27)
ALTER TABLE members
    ADD COLUMN IF NOT EXISTS death_date DATE,
    ADD COLUMN IF NOT EXISTS burial_place VARCHAR(255);

-- Ràng buộc kiểm tra tính hợp lý của ngày mất và ngày sinh
ALTER TABLE members
    DROP CONSTRAINT IF EXISTS chk_members_death_date_after_birth;

ALTER TABLE members
    ADD CONSTRAINT chk_members_death_date_after_birth
    CHECK (death_date IS NULL OR date_of_birth IS NULL OR death_date >= date_of_birth);

COMMENT ON COLUMN members.death_date IS 'Ngày mất theo Dương lịch của thành viên (US-27)';
COMMENT ON COLUMN members.burial_place IS 'Nơi an táng, phần mộ hoặc nghĩa trang yên nghỉ của thành viên (US-27)';
COMMENT ON COLUMN members.death_date_lunar IS 'Ngày giỗ theo Âm lịch (ví dụ: 15/02 Giáp Thìn) tự động tính toán (US-27)';
