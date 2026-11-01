package com.familyfund.event.internal.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for requesting modification of an existing registration (US-06).
 */
public record RequestModifyRegistrationRequest(
        @NotNull(message = "Số lượng người tham dự đề xuất không được để trống")
        @Min(value = 1, message = "Số lượng người tham dự đề xuất phải ít nhất là 1")
        Integer proposedTotalAttendees,

        @Min(value = 0, message = "Số lượng suất ăn chay đề xuất không được là số âm")
        Integer proposedVegetarianMeals,

        @Min(value = 0, message = "Số lượng suất ăn mặn đề xuất không được là số âm")
        Integer proposedSavoryMeals,

        @Size(max = 255, message = "Ghi chú ăn uống đề xuất không được vượt quá 255 ký tự")
        String proposedDietaryRequirements,

        @Size(max = 255, message = "Ghi chú phương tiện đề xuất không được vượt quá 255 ký tự")
        String proposedTransportationNotes,

        @NotBlank(message = "Lý do đề xuất thay đổi không được để trống")
        @Size(max = 255, message = "Lý do đề xuất thay đổi không được vượt quá 255 ký tự")
        String modificationReason
) {
    public RequestModifyRegistrationRequest {
        if (proposedVegetarianMeals == null) {
            proposedVegetarianMeals = 0;
        }
        if (proposedSavoryMeals == null) {
            proposedSavoryMeals = 0;
        }
    }
}
