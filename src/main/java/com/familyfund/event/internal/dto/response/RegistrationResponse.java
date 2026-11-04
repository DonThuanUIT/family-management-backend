package com.familyfund.event.internal.dto.response;

import com.familyfund.event.api.dto.RegistrationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Detailed Response DTO for a family event registration (US-06).
 */
public record RegistrationResponse(
        String registrationId,
        String eventId,
        String representativeMemberId,
        String representativeMemberName,
        int totalAttendees,
        int vegetarianMeals,
        int savoryMeals,
        String dietaryRequirements,
        String transportationNotes,
        Instant registrationDate,
        RegistrationStatus status,
        Integer proposedTotalAttendees,
        Integer proposedVegetarianMeals,
        Integer proposedSavoryMeals,
        String proposedDietaryRequirements,
        String proposedTransportationNotes,
        String modificationReason,
        String reviewedByAccountId,
        Instant reviewedAt,
        String rejectionReason,
        Instant updatedAt,
        List<RegistrationMemberDetailResponse> attendingMembers
) {
}
