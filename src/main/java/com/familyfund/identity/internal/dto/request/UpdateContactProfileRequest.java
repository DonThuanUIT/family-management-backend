package com.familyfund.identity.internal.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating member contact details (US-22).
 * Deliberately omits full_name, date_of_birth, generation, etc.
 */
public record UpdateContactProfileRequest(
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^(0[3|5|7|8|9])[0-9]{8}$", message = "Số điện thoại không hợp lệ (yêu cầu 10 chữ số chuẩn Việt Nam)")
        String phoneNumber,

        @Email(message = "Hòm thư điện tử (email) không đúng định dạng")
        @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
        String email,

        @Size(max = 255, message = "Địa chỉ liên lạc không được vượt quá 255 ký tự")
        String address
) {
}
