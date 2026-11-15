package com.familyfund.event.api.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Suggested payload for creating the "Đám giỗ" event of one death anniversary (US-40).
 *
 * <p>Field-for-field compatible with the body of {@code POST /api/v1/events}
 * ({@link com.familyfund.event.internal.dto.request.CreateEventRequest} shape), so the client can send
 * {@link #requestBody()} straight back after the user reviews it — nothing is persisted on the server side until that
 * POST. The suggestion is intentionally a proposal, not a side effect: the cron job never creates events by itself.</p>
 *
 * @param suggestionId      synthetic id for client-side keying (not persisted)
 * @param memberId          deceased member the anniversary belongs to
 * @param eventName         proposed event name, e.g. {@code "Đám giỗ Cụ Nguyễn Văn An (15/02 ÂL)"}
 * @param eventDatetime     proposed start, 08:00 (GMT+7) on the anniversary day
 * @param eventDateLunar    proposed lunar label, e.g. {@code "15/02 Bính Ngọ"}
 * @param location          proposed location (branch name, else the clan name)
 * @param estimatedBudget   proposed budget, {@code null} = let the user decide
 * @param description       human-readable rationale: who, which anniversary year, which branch
 * @param daysRemaining     days from today to the anniversary
 * @param requestBody       exact JSON body to POST to {@code /api/v1/events}
 */
@Builder
public record EventSuggestionDto(
        String suggestionId,
        String memberId,
        String eventName,
        Instant eventDatetime,
        String eventDateLunar,
        String location,
        BigDecimal estimatedBudget,
        String description,
        long daysRemaining,
        CreateEventSuggestion requestBody
) {

    /**
     * The proposal as it should be sent to {@code POST /api/v1/events} (same field names as
     * {@code CreateEventRequest}).
     */
    @Builder
    public record CreateEventSuggestion(
            String eventName,
            Instant eventDatetime,
            String eventDateLunar,
            String location,
            BigDecimal estimatedBudget,
            String description
    ) {}
}
