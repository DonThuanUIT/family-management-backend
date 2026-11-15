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

    /**
     * Events of one clan whose start falls inside {@code [from, to)} — used by the US-40 anniversary matcher to know
     * whether the suggested "Đám giỗ" event already exists, without loading the whole event list of the clan.
     */
    @Query("""
            SELECT e FROM Event e
            WHERE e.clanId = :clanId
              AND e.eventDatetime >= :from
              AND e.eventDatetime < :to
            """)
    List<Event> findEventsInWindow(@Param("clanId") String clanId,
                                   @Param("from") java.time.Instant from,
                                   @Param("to") java.time.Instant to);

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
