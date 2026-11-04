package com.familyfund.event.internal.dto.response;

/**
 * Response DTO representing an individual family member participating in the event registration (US-06).
 */
public record RegistrationMemberDetailResponse(
        String memberId,
        String fullName,
        Boolean isCheckedIn
) {
}
