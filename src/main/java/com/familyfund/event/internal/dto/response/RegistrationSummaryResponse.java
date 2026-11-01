package com.familyfund.event.internal.dto.response;

/**
 * Response DTO providing aggregated registration metrics for an event (US-06).
 */
public record RegistrationSummaryResponse(
        String eventId,
        long totalRegistrations,
        long totalAttendees,
        long totalVegetarianMeals,
        long totalSavoryMeals
) {
}
