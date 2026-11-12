package com.familyfund.event.internal.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Payload cập nhật trực tiếp thông tin đăng ký tham dự của hộ gia đình
 * trước khi Ban tổ chức chốt danh sách (US-41).
 */
@Builder
public record UpdateRegistrationRequest(
        @NotNull(message = "Tổng số người tham dự không được để trống")
        @Min(value = 1, message = "Tổng số người tham dự phải tối thiểu là 1")
        Integer totalAttendees,

        @Min(value = 0, message = "Số suất ăn chay không được âm")
        Integer vegetarianMeals,

        @Min(value = 0, message = "Số suất ăn mặn không được âm")
        Integer savoryMeals,

        @Size(max = 500, message = "Ghi chú khẩu phần ăn không được vượt quá 500 ký tự")
        String dietaryRequirements,

        @Size(max = 500, message = "Thông tin phương tiện di chuyển không được vượt quá 500 ký tự")
        String transportationNotes
) {}
