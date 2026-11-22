package com.familyfund.event.internal.repository;

import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.entity.EventPersonnelId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventPersonnelRepository extends JpaRepository<EventPersonnel, EventPersonnelId> {

    List<EventPersonnel> findAllByEventId(String eventId);

    List<EventPersonnel> findAllByEventIdAndMemberId(String eventId, String memberId);

    void deleteAllByEventId(String eventId);

    boolean existsByEventIdAndMemberIdAndEventRole(String eventId, String memberId, EventRole eventRole);

    @Query("""
            SELECT CASE WHEN COUNT(ep) > 0 THEN true ELSE false END
            FROM EventPersonnel ep
            JOIN Event e ON ep.eventId = e.eventId
            WHERE e.clanId = :clanId
              AND ep.memberId = :memberId
              AND ep.eventRole = :eventRole
            """)
    boolean existsByClanIdAndMemberIdAndEventRole(
            @Param("clanId") String clanId,
            @Param("memberId") String memberId,
            @Param("eventRole") EventRole eventRole
    );
}
