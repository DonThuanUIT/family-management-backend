package com.familyfund.event.internal.service;

import com.familyfund.event.internal.dto.request.AssignPersonnelRequest;
import com.familyfund.event.internal.dto.request.CreateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventRequest;
import com.familyfund.event.internal.dto.request.UpdateEventStatusRequest;
import com.familyfund.event.internal.dto.response.EventBudgetSummaryResponse;
import com.familyfund.event.internal.dto.response.EventDetailResponse;
import com.familyfund.event.internal.dto.response.EventNotificationResponse;
import com.familyfund.event.internal.dto.response.EventPersonnelResponse;
import com.familyfund.event.internal.dto.response.EventResponse;

import java.util.List;

/**
 * Service contract for managing family events, status transitions, personnel assignments,
 * and budget comparison / overrun warnings.
 */
public interface EventService {

    EventResponse createEvent(CreateEventRequest request);

    EventResponse updateEvent(String eventId, UpdateEventRequest request);

    EventResponse updateEventStatus(String eventId, UpdateEventStatusRequest request);

    List<EventPersonnelResponse> assignPersonnel(String eventId, AssignPersonnelRequest request);

    EventDetailResponse getEventDetail(String eventId);

    List<EventResponse> listEvents();

    List<EventNotificationResponse> getEventNotifications(String eventId);

    /**
     * Tổng hợp và so sánh {@code estimated_budget} của sự kiện với tổng {@code advance_budget} (công việc),
     * tổng {@code advance_amount} (tạm ứng) và {@code actual_expense_amount} (thực chi);
     * trả về cờ cảnh báo ngay khi tổng tạm ứng/thực chi vượt mức dự kiến (US-37).
     *
     * @param eventId Mã sự kiện cần tổng hợp ngân sách
     * @return {@link EventBudgetSummaryResponse} chứa báo cáo so sánh ngân sách và cờ cảnh báo
     */
    EventBudgetSummaryResponse getEventBudgetSummary(String eventId);
}
