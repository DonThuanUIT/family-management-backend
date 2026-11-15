package com.familyfund.event.internal.mapper;

import com.familyfund.event.internal.dto.response.EventDetailResponse;
import com.familyfund.event.internal.dto.response.EventNotificationResponse;
import com.familyfund.event.internal.dto.response.EventPersonnelResponse;
import com.familyfund.event.internal.dto.response.EventResponse;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventNotification;
import com.familyfund.event.internal.entity.EventPersonnel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventMapper {

    default EventResponse toResponse(Event event) {
        return toResponse(event, 0);
    }

    default EventResponse toResponse(Event event, int currentAttendees) {
        if (event == null) {
            return null;
        }
        Integer maxCap = event.getMaxCapacity();
        Integer remaining = maxCap != null ? Math.max(0, maxCap - currentAttendees) : null;
        return new EventResponse(
                event.getEventId(),
                event.getClanId(),
                event.getEventName(),
                event.getEventDatetime(),
                event.getEventDateLunar(),
                event.getLocation(),
                event.getEstimatedBudget(),
                event.getDescription(),
                event.getStatus(),
                event.getCreatedBy(),
                event.getClosedAt(),
                event.getRegistrationDeadline(),
                maxCap,
                currentAttendees,
                remaining
        );
    }

    List<EventResponse> toResponseList(List<Event> events);

    @Mapping(target = "memberName", ignore = true)
    EventPersonnelResponse toPersonnelResponse(EventPersonnel personnel);

    List<EventPersonnelResponse> toPersonnelResponseList(List<EventPersonnel> personnel);

    default EventDetailResponse toDetailResponse(Event event, List<EventPersonnelResponse> personnelResponses) {
        return toDetailResponse(event, personnelResponses, 0);
    }

    default EventDetailResponse toDetailResponse(
            Event event,
            List<EventPersonnelResponse> personnelResponses,
            int currentAttendees) {
        if (event == null) {
            return null;
        }
        Integer maxCap = event.getMaxCapacity();
        Integer remaining = maxCap != null ? Math.max(0, maxCap - currentAttendees) : null;
        return new EventDetailResponse(
                event.getEventId(),
                event.getClanId(),
                event.getEventName(),
                event.getEventDatetime(),
                event.getEventDateLunar(),
                event.getLocation(),
                event.getEstimatedBudget(),
                event.getDescription(),
                event.getStatus(),
                event.getCreatedBy(),
                event.getClosedAt(),
                event.getRegistrationDeadline(),
                maxCap,
                currentAttendees,
                remaining,
                personnelResponses
        );
    }

    EventNotificationResponse toNotificationResponse(EventNotification notification);

    List<EventNotificationResponse> toNotificationResponseList(List<EventNotification> notifications);
}
