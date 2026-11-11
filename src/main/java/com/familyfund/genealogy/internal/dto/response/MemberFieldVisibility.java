package com.familyfund.genealogy.internal.dto.response;

import lombok.Builder;

/**
 * Field-level visibility metadata for member profile responses (US-25).
 * Indicates to API consumers whether each sensitive field was authorized for viewing
 * or restricted by privacy policy.
 */
@Builder
public record MemberFieldVisibility(
        FieldVisibility dateOfBirth,
        FieldVisibility phoneNumber,
        FieldVisibility email
) {
    public static MemberFieldVisibility allVisible() {
        return new MemberFieldVisibility(FieldVisibility.VISIBLE, FieldVisibility.VISIBLE, FieldVisibility.VISIBLE);
    }

    public static MemberFieldVisibility allRestricted() {
        return new MemberFieldVisibility(FieldVisibility.RESTRICTED, FieldVisibility.RESTRICTED, FieldVisibility.RESTRICTED);
    }
}
