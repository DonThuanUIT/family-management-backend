package com.familyfund.event.api.dto;

/**
 * Public summary DTO of event registration metrics for cross-module consumption (US-06).
 */
public record RegistrationSummaryDto(
        String eventId,
        long totalRegistrations,
        long totalAttendees,
        long totalVegetarianMeals,
        long totalSavoryMeals
) {
}
