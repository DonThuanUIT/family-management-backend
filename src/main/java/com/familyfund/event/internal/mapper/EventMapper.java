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

    EventResponse toResponse(Event event);

    List<EventResponse> toResponseList(List<Event> events);

    @Mapping(target = "memberName", ignore = true)
    EventPersonnelResponse toPersonnelResponse(EventPersonnel personnel);

    List<EventPersonnelResponse> toPersonnelResponseList(List<EventPersonnel> personnel);

    @Mapping(target = "personnel", source = "personnelResponses")
    EventDetailResponse toDetailResponse(Event event, List<EventPersonnelResponse> personnelResponses);

    EventNotificationResponse toNotificationResponse(EventNotification notification);

    List<EventNotificationResponse> toNotificationResponseList(List<EventNotification> notifications);
}
