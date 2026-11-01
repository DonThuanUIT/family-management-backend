package com.familyfund.event.internal.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for submitting a family event registration (US-06).
 */
public record RegisterAttendeesRequest(
        @Size(max = 20, message = "Mã người đại diện không được vượt quá 20 ký tự")
        String representativeMemberId,

        @NotNull(message = "Số lượng người tham dự không được để trống")
        @Min(value = 1, message = "Số lượng người tham dự phải ít nhất là 1")
        Integer totalAttendees,

        @Min(value = 0, message = "Số lượng suất ăn chay không được là số âm")
        Integer vegetarianMeals,

        @Min(value = 0, message = "Số lượng suất ăn mặn không được là số âm")
        Integer savoryMeals,

        @Size(max = 255, message = "Ghi chú ăn uống không được vượt quá 255 ký tự")
        String dietaryRequirements,

        @Size(max = 255, message = "Ghi chú phương tiện không được vượt quá 255 ký tự")
        String transportationNotes,

        List<@Size(max = 20, message = "Mã thành viên tham gia không được vượt quá 20 ký tự") String> attendingMemberIds
) {
    public RegisterAttendeesRequest {
        if (vegetarianMeals == null) {
            vegetarianMeals = 0;
        }
        if (savoryMeals == null) {
            savoryMeals = 0;
        }
        if (attendingMemberIds == null) {
            attendingMemberIds = List.of();
        }
    }
}
