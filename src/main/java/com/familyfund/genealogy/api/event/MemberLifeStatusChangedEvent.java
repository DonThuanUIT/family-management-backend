package com.familyfund.genealogy.api.event;

import java.time.LocalDate;

/**
 * Domain Event phát ra khi tình trạng sống/đã mất của một thành viên thay đổi (US-27).
 * Cho phép các bounded context khác (như `identity`) phản ứng khóa hoặc khôi phục tài khoản.
 */
public record MemberLifeStatusChangedEvent(
        String memberId,
        String clanId,
        String status,
        LocalDate deathDate,
        String deathDateLunar
) {
    public boolean isDeceased() {
        return "Deceased".equalsIgnoreCase(status);
    }
}
