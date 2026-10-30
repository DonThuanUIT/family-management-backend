package com.familyfund.identity.internal.dto.response;

import java.time.LocalDate;

public record MemberResponse(
        String memberId,
        String fullName,
        String gender,
        Integer generation,
        LocalDate dateOfBirth,
        String branchId
) {
}
