package com.familyfund.genealogy.internal.dto.response;

import lombok.Builder;

import java.time.Instant;

/**
 * Read model of a member's personal privacy consent settings (US-25).
 */
@Builder
public record MemberPrivacySettingResponse(
        String memberId,
        boolean shareDateOfBirth,
        boolean sharePhoneNumber,
        boolean shareEmail,
        Instant updatedAt
) {}
