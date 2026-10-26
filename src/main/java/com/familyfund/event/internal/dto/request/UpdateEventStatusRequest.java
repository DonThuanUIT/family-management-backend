package com.familyfund.event.internal.dto.request;

import com.familyfund.event.api.dto.EventStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for transitioning the lifecycle status of an event.
 */
public record UpdateEventStatusRequest(
        @NotNull(message = "Trạng thái mới không được để trống")
        EventStatus status
) {
}
