package com.familyfund.genealogy.api.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Cross-module view of a genealogy member profile created by the identity module (US-18).
 *
 * <p>Mirrors the columns of table {@code members} plus the parent data resolved through
 * {@code members.branch_id -> family_branches}: {@code branchName} and {@code clanId}. Returned by
 * {@link com.familyfund.genealogy.api.GenealogyMemberCommandService#createVirtualMember} so the
 * identity module can build the position link code response without reaching into genealogy internals.</p>
 */
public record VirtualMemberDto(
        String memberId,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        Integer generation,
        String status,
        String deathDateLunar,
        String branchId,
        String branchName,
        String clanId,
        Boolean isPlaceholder,
        Instant createdAt
) {}
