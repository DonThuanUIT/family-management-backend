package com.familyfund.event.internal.service;

import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventQueryServiceImpl implements EventQueryService {

    private final EventRepository eventRepository;
    private final EventPersonnelRepository eventPersonnelRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<EventSummaryDto> findEventSummary(String eventId) {
        return eventRepository.findById(eventId)
                .map(e -> new EventSummaryDto(
                        e.getEventId(),
                        e.getClanId(),
                        e.getEventName(),
                        e.getStatus(),
                        e.getEventDatetime()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEventClosed(String eventId) {
        return eventRepository.findById(eventId)
                .map(e -> e.getStatus() == EventStatus.Closed)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<EventRole> getMemberRolesInEvent(String eventId, String memberId) {
        return eventPersonnelRepository.findAllByEventIdAndMemberId(eventId, memberId).stream()
                .map(EventPersonnel::getEventRole)
                .collect(Collectors.toSet());
    }
}
