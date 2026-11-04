package com.familyfund.genealogy.internal.dto.request;

import lombok.Builder;

/**
 * Filter criteria for member search (US-30 / Task #111).
 * Supports fuzzy full-text and unaccented search across name, memberId, generation, branch, and status.
 */
@Builder
public record MemberSearchCriteria(
        String keyword,
        String memberId,
        String fullName,
        Integer generation,
        String branchId,
        String status
) {
    public static MemberSearchCriteria of(
            String keyword,
            String memberId,
            String fullName,
            Integer generation,
            String branchId,
            String status
    ) {
        return new MemberSearchCriteria(
                clean(keyword),
                clean(memberId),
                clean(fullName),
                generation,
                clean(branchId),
                clean(status)
        );
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
