package com.familyfund.genealogy.api.dto;

/**
 * Summary DTO of a genealogy member exposed across bounded contexts.
 */
public record MemberSummaryDto(
        String memberId,
        String fullName,
        String clanId,
        String branchId
) {
}
