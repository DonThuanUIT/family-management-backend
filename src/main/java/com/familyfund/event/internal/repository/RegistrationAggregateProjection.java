package com.familyfund.event.internal.repository;

/**
 * Spring Data JPA projection for aggregate registration metrics (US-06).
 */
public interface RegistrationAggregateProjection {

    long getTotalRegistrations();

    long getTotalAttendees();

    long getTotalVegetarianMeals();

    long getTotalSavoryMeals();
}

