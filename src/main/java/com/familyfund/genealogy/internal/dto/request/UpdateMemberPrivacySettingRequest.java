package com.familyfund.genealogy.internal.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Request payload for updating a member's personal privacy consent settings (US-25).
 */
@Builder
public record UpdateMemberPrivacySettingRequest(
        @NotNull(message = "shareDateOfBirth must not be null")
        Boolean shareDateOfBirth,

        @NotNull(message = "sharePhoneNumber must not be null")
        Boolean sharePhoneNumber,

        @NotNull(message = "shareEmail must not be null")
        Boolean shareEmail
) {}
