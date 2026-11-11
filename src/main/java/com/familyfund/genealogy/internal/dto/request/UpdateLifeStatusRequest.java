package com.familyfund.genealogy.internal.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload yêu cầu cập nhật tình trạng sống / đã mất của thành viên (US-27).
 */
public record UpdateLifeStatusRequest(
        @NotBlank(message = "Trạng thái thành viên không được để trống")
        @Pattern(regexp = "^(?i)(Living|Deceased)$", message = "Trạng thái thành viên chỉ có thể là 'Living' hoặc 'Deceased'")
        String status,

        @PastOrPresent(message = "Ngày mất không được nằm trong tương lai")
        LocalDate deathDate,

        @Size(max = 255, message = "Nơi an táng không được vượt quá 255 ký tự")
        String burialPlace
) {
    @AssertTrue(message = "Ngày mất là bắt buộc khi cập nhật thành viên sang trạng thái 'Đã mất' (Deceased)")
    public boolean isDeathDateValidForDeceased() {
        if ("Deceased".equalsIgnoreCase(status)) {
            return deathDate != null;
        }
        return true;
    }

    public boolean isDeceased() {
        return "Deceased".equalsIgnoreCase(status);
    }
}
