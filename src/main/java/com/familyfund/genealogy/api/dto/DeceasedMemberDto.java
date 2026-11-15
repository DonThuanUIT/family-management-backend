package com.familyfund.genealogy.api.dto;

import lombok.Builder;

/**
 * A deceased member carrying a lunar death-anniversary string, as needed by the US-40 reminder job.
 *
 * <p>Exported from {@code genealogy::api} so the {@code event} module can list candidates without touching
 * {@code members} or {@code family_branches}. Only ceremonial fields are included — no birth date, phone or email.</p>
 *
 * @param memberId        {@code members.member_id}
 * @param clanId          owning clan (through {@code family_branches.clan_id})
 * @param fullName        display name
 * @param generation      generation of the deceased
 * @param branchId        branch the deceased belongs to
 * @param branchName      branch display name (may be {@code null})
 * @param deathDate       solar death date (may be {@code null})
 * @param deathDateLunar  stored lunar anniversary, e.g. {@code "15/02 Giáp Thìn"}
 */
@Builder
public record DeceasedMemberDto(
        String memberId,
        String clanId,
        String fullName,
        Integer generation,
        String branchId,
        String branchName,
        java.time.LocalDate deathDate,
        String deathDateLunar
) {}
