package com.familyfund.event.internal.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request payload for updating details and schedule of an existing event.
 */
public record UpdateEventRequest(
        @NotBlank(message = "Tên sự kiện không được để trống")
        @Size(max = 100, message = "Tên sự kiện tối đa 100 ký tự")
        String eventName,

        @NotNull(message = "Thời gian tổ chức dương lịch không được để trống")
        Instant eventDatetime,

        @Size(max = 50, message = "Ngày âm lịch tối đa 50 ký tự")
        String eventDateLunar,

        @NotBlank(message = "Địa điểm tổ chức không được để trống")
        @Size(max = 255, message = "Địa điểm tổ chức tối đa 255 ký tự")
        String location,

        @DecimalMin(value = "0.0", message = "Dự toán ngân sách phải lớn hơn hoặc bằng 0")
        BigDecimal estimatedBudget,

        String description,

        Instant registrationDeadline,

        @Min(value = 1, message = "Sức chứa tối đa phải lớn hơn hoặc bằng 1")
        Integer maxCapacity
) {
    public UpdateEventRequest(
            String eventName,
            Instant eventDatetime,
            String eventDateLunar,
            String location,
            BigDecimal estimatedBudget,
            String description
    ) {
        this(eventName, eventDatetime, eventDateLunar, location, estimatedBudget, description, null, null);
    }
}
