package com.familyfund.event.internal.dto.response;

public record MemberRoleOptionDto(
        String memberId,
        String fullName,
        String branchId,
        String branchName,
        boolean hasActiveAccount,
        String accountId
) {
}
