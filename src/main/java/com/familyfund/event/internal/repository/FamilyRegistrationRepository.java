package com.familyfund.event.internal.repository;

import com.familyfund.event.api.dto.RegistrationStatus;
import com.familyfund.event.internal.entity.FamilyRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for `FamilyRegistration` (table `family_registrations`).
 */
@Repository
public interface FamilyRegistrationRepository extends JpaRepository<FamilyRegistration, String> {

    Optional<FamilyRegistration> findByRegistrationId(String registrationId);

    Optional<FamilyRegistration> findByRegistrationIdAndEvent_ClanId(String registrationId, String clanId);

    Optional<FamilyRegistration> findByEventIdAndRepresentativeMemberId(String eventId, String representativeMemberId);

    Optional<FamilyRegistration> findByEventIdAndRepresentativeMemberIdAndEvent_ClanId(
            String eventId, String representativeMemberId, String clanId);

    boolean existsByEventIdAndRepresentativeMemberId(String eventId, String representativeMemberId);

    boolean existsByEventIdAndRepresentativeMemberIdAndStatusNot(
            String eventId, String representativeMemberId, RegistrationStatus status);

    List<FamilyRegistration> findAllByEvent_EventIdAndEvent_ClanIdOrderByRegistrationDateDesc(
            String eventId, String clanId);

    Page<FamilyRegistration> findAllByEvent_EventIdAndEvent_ClanIdOrderByRegistrationDateDesc(
            String eventId, String clanId, Pageable pageable);

    List<FamilyRegistration> findAllByEvent_EventIdAndEvent_ClanIdAndStatusOrderByRegistrationDateDesc(
            String eventId, String clanId, RegistrationStatus status);

    long countByEvent_EventIdAndEvent_ClanId(String eventId, String clanId);

    long countByEvent_EventIdAndEvent_ClanIdAndStatusNot(
            String eventId, String clanId, RegistrationStatus status);

    /**
     * Aggregates total registrations, attendees, vegetarian meals and savory meals
     * excluding cancelled registrations.
     */
    @Query("""
        SELECT
            COUNT(r) AS totalRegistrations,
            COALESCE(SUM(r.totalAttendees), 0) AS totalAttendees,
            COALESCE(SUM(r.vegetarianMeals), 0) AS totalVegetarianMeals,
            COALESCE(SUM(r.savoryMeals), 0) AS totalSavoryMeals
        FROM FamilyRegistration r
        WHERE r.eventId = :eventId
          AND r.event.clanId = :clanId
          AND r.status != 'Cancelled'
    """)
    RegistrationAggregateProjection getAggregateSummary(
            @Param("eventId") String eventId,
            @Param("clanId") String clanId);
}

