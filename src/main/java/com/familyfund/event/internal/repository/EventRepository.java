package com.familyfund.event.internal.repository;

import com.familyfund.event.internal.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {

    Optional<Event> findByEventIdAndClanId(String eventId, String clanId);

    List<Event> findAllByClanIdOrderByEventDatetimeDesc(String clanId);

    boolean existsByEventIdAndClanId(String eventId, String clanId);

    @Query("""
        SELECT COALESCE(SUM(r.totalAttendees), 0)
        FROM FamilyRegistration r
        WHERE r.eventId = :eventId
          AND r.event.clanId = :clanId
          AND r.status != 'Cancelled'
    """)
    long sumActiveAttendeesByEventIdAndClanId(
            @Param("eventId") String eventId,
            @Param("clanId") String clanId);
}
