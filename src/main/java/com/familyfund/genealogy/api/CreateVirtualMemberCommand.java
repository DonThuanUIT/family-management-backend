package com.familyfund.genealogy.api;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Command payload for creating a virtual (placeholder) member profile that no user account owns yet (US-18).
 *
 * <p>The profile is a leaf placeholder: it is written with {@code members.is_placeholder = true} and is
 * meant to be claimed later through the position link code issued by the identity module
 * (US-19). Nothing here accepts a {@code member_id} or the placeholder flag from the caller.</p>
 *
 * @param clanId         tenant the caller is acting in; must match the {@code TenantContext} tenant
 * @param branchId       branch that receives the profile ({@code members.branch_id -> family_branches.branch_id})
 * @param fullName       {@code members.full_name} (trimmed by the service)
 * @param dateOfBirth    {@code members.date_of_birth} (nullable)
 * @param gender         {@code Male | Female | Other}
 * @param generation     {@code members.generation}, must be &ge; the branch generation level
 * @param status         {@code Living | Deceased}; null falls back to {@code Living}
 * @param deathDateLunar {@code members.death_date_lunar} (nullable)
 */
@Builder
public record CreateVirtualMemberCommand(
        String clanId,
        String branchId,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        Integer generation,
        String status,
        String deathDateLunar
) {}
