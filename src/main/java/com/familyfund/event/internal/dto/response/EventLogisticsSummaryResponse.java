package com.familyfund.event.internal.dto.response;

import com.familyfund.event.api.dto.RegistrationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Real-time event logistics summary DTO for Organizers (BTC) — US-42.
 * Aggregates households, attendees, savory/vegetarian meals, and shuttle seats,
 * with optional branch filtering and detailed household rows for dashboard & export.
 */
public record EventLogisticsSummaryResponse(
        String eventId,
        String eventName,
        String branchId,
        String branchName,
        long totalRegistrations,
        long totalAttendees,
        long totalSavoryMeals,
        long totalVegetarianMeals,
        long totalShuttleSeats,
        List<BranchOption> availableBranches,
        List<HouseholdLogisticsItem> registrations
) {
    public record BranchOption(
            String branchId,
            String branchName
    ) {
    }

    public record HouseholdLogisticsItem(
            String registrationId,
            String representativeMemberId,
            String representativeName,
            String branchId,
            String branchName,
            int totalAttendees,
            int savoryMeals,
            int vegetarianMeals,
            int shuttleSeats,
            String dietaryRequirements,
            String transportationNotes,
            RegistrationStatus status,
            Instant registrationDate
    ) {
    }
}
