package com.familyfund.event.internal.repository;

import com.familyfund.event.internal.entity.EventNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventNotificationRepository extends JpaRepository<EventNotification, String> {

    List<EventNotification> findAllByEventIdOrderBySentTimeDesc(String eventId);
}
