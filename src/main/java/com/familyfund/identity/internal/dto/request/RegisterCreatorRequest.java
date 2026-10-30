package com.familyfund.identity.internal.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterCreatorRequest(
        @NotBlank(message = "Tên dòng họ không được để trống")
        @Size(max = 150, message = "Tên dòng họ không được vượt quá 150 ký tự")
        String clanName,

        @Size(max = 100, message = "Tên thủy tổ không được vượt quá 100 ký tự")
        String ancestorName,

        @Size(max = 255, message = "Quê quán không được vượt quá 255 ký tự")
        String originLocation,

        Integer establishedYear,

        String description,

        @NotBlank(message = "Họ và tên người khởi tạo không được để trống")
        @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
        String fullName,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^[0-9+]{8,15}$", message = "Số điện thoại không đúng định dạng (8-15 ký tự số)")
        String phoneNumber,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, message = "Mật khẩu phải có độ dài tối thiểu 6 ký tự")
        String password,

        @NotBlank(message = "Giới tính không được để trống")
        @Pattern(regexp = "^(Male|Female|Other)$", message = "Giới tính phải là Male, Female hoặc Other")
        String gender,

        @Email(message = "Địa chỉ email không đúng định dạng")
        @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
        String email,

        LocalDate dateOfBirth,

        @Min(value = 1, message = "Đời / thế hệ phải từ 1 trở lên")
        Integer generation
) {
}
