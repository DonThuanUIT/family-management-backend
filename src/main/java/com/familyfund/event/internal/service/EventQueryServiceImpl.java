package com.familyfund.event.internal.service;

import com.familyfund.event.api.EventQueryService;
import com.familyfund.event.api.dto.EventRole;
import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.EventSummaryDto;
import com.familyfund.event.api.dto.EventTaskSummaryDto;
import com.familyfund.event.api.dto.RegistrationSummaryDto;
import com.familyfund.event.internal.entity.EventPersonnel;
import com.familyfund.event.internal.mapper.RegistrationMapper;
import com.familyfund.event.internal.repository.EventPersonnelRepository;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.event.internal.repository.FamilyRegistrationRepository;
import com.familyfund.event.internal.repository.RegistrationAggregateProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventQueryServiceImpl implements EventQueryService {

    private final EventRepository eventRepository;
    private final EventPersonnelRepository eventPersonnelRepository;
    private final FamilyRegistrationRepository familyRegistrationRepository;
    private final EventTaskRepository eventTaskRepository;
    private final RegistrationMapper registrationMapper;

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

    @Override
    @Transactional(readOnly = true)
    public Optional<RegistrationSummaryDto> getRegistrationSummary(String eventId) {
        return eventRepository.findById(eventId)
                .map(e -> {
                    RegistrationAggregateProjection projection =
                            familyRegistrationRepository.getAggregateSummary(eventId, e.getClanId());
                    return registrationMapper.toSummaryDto(eventId, projection);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventTaskSummaryDto> findTaskSummary(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        return eventTaskRepository.findById(taskId.trim())
                .flatMap(task -> eventRepository.findById(task.getEventId())
                        .map(event -> new EventTaskSummaryDto(
                                task.getTaskId(),
                                task.getEventId(),
                                event.getClanId(),
                                task.getTaskName(),
                                task.getAdvanceBudget(),
                                task.getDeadline(),
                                task.getStatus(),
                                task.getAssignedMemberId(),
                                task.getAssignedBranchId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findOrganizerMemberIds(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Set.of();
        }
        return eventPersonnelRepository.findAllByEventId(eventId.trim()).stream()
                .map(EventPersonnel::getMemberId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasEventRoleInClan(String clanId, String memberId, EventRole eventRole) {
        if (clanId == null || clanId.isBlank() || memberId == null || memberId.isBlank() || eventRole == null) {
            return false;
        }
        return eventPersonnelRepository.existsByClanIdAndMemberIdAndEventRole(
                clanId.trim(), memberId.trim(), eventRole);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> findEventNamesByIds(Collection<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        Set<String> normalizedIds = eventIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        eventRepository.findAllById(normalizedIds).forEach(event -> {
            if (event.getEventId() != null && event.getEventName() != null) {
                result.put(event.getEventId(), event.getEventName());
            }
        });
        return result;
    }
}
