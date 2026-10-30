package com.familyfund.event.internal.repository;

import com.familyfund.event.internal.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {

    Optional<Event> findByEventIdAndClanId(String eventId, String clanId);

    List<Event> findAllByClanIdOrderByEventDatetimeDesc(String clanId);

    boolean existsByEventIdAndClanId(String eventId, String clanId);
}
