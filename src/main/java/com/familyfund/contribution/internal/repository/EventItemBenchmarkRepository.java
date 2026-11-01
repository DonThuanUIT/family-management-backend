package com.familyfund.contribution.internal.repository;

import com.familyfund.contribution.internal.entity.EventItemBenchmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventItemBenchmarkRepository extends JpaRepository<EventItemBenchmark, String> {

    List<EventItemBenchmark> findByEventIdOrderByCreatedAtAsc(String eventId);

    Optional<EventItemBenchmark> findByBenchmarkIdAndEventId(String benchmarkId, String eventId);

    boolean existsByEventIdAndItemNameIgnoreCase(String eventId, String itemName);
}
