package com.familyfund.event.internal.dto.request;

import com.familyfund.event.api.dto.EventRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for assigning personnel to an event.
 */
public record AssignPersonnelRequest(
        @NotEmpty(message = "Danh sách nhân sự không được để trống")
        @Valid
        List<PersonnelItem> personnel
) {
    public record PersonnelItem(
            @NotBlank(message = "Mã thành viên không được để trống")
            @Size(max = 20, message = "Mã thành viên tối đa 20 ký tự")
            String memberId,

            @NotNull(message = "Vai trò trong sự kiện không được để trống")
            EventRole role
    ) {
    }
}
