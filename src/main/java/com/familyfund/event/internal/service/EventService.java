package com.familyfund.event.internal.service;

import com.familyfund.event.internal.dto.request.AssignPersonnelRequest;
import com.familyfund.event.internal.dto.request.CreateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventStatusRequest;
import com.familyfund.event.internal.dto.response.EventDetailResponse;
import com.familyfund.event.internal.dto.response.EventNotificationResponse;
import com.familyfund.event.internal.dto.response.EventPersonnelResponse;
import com.familyfund.event.internal.dto.response.EventResponse;

import java.util.List;

/**
 * Service contract for managing family events, status transitions, and personnel assignments.
 */
public interface EventService {

    EventResponse createEvent(CreateEventRequest request);

    EventResponse updateEvent(String eventId, UpdateEventRequest request);

    EventResponse updateEventStatus(String eventId, UpdateEventStatusRequest request);

    List<EventPersonnelResponse> assignPersonnel(String eventId, AssignPersonnelRequest request);

    EventDetailResponse getEventDetail(String eventId);

    List<EventResponse> listEvents();

    List<EventNotificationResponse> getEventNotifications(String eventId);
}
