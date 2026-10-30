package com.familyfund.identity.api.dto;

import com.familyfund.identity.internal.entity.ProposalType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;

/**
 * Request payload for prospective members submitting self-onboarding join proposals (US-17).
 */
@Builder
public record SubmitJoinRequest(
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Số điện thoại không đúng định dạng (VD: 0912345678)")
        String phoneNumber,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 50, message = "Mật khẩu phải từ 6 đến 50 ký tự")
        String password,

        @Pattern(regexp = "^$|^[A-Za-z0-9+_.-]+@(.+)$", message = "Email không đúng định dạng")
        String email,

        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
        String fullName,

        @Past(message = "Ngày sinh phải trong quá khứ")
        LocalDate dateOfBirth,

        @NotBlank(message = "Giới tính không được để trống")
        @Pattern(regexp = "^(Male|Female|Other)$", message = "Giới tính phải là Male, Female hoặc Other")
        String gender,

        @NotNull(message = "Loại đề xuất vị trí không được để trống")
        ProposalType proposalType,

        @NotBlank(message = "Chi nhánh mục tiêu không được để trống")
        String targetBranchId,

        @Size(max = 100, message = "Tên chi nhánh mới không được vượt quá 100 ký tự")
        String proposedNewBranchName,

        @NotNull(message = "Đời thứ đề xuất không được để trống")
        @Min(value = 1, message = "Đời thứ đề xuất phải lớn hơn hoặc bằng 1")
        Integer proposedGeneration,

        @Size(max = 255, message = "Ghi chú mối quan hệ không được vượt quá 255 ký tự")
        String relationshipNote
) {}

