package com.familyfund.genealogy.internal.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

/**
 * Payload xác nhận nhập hàng loạt các dòng thành viên hợp lệ (US-31).
 */
@Builder
public record ConfirmMemberImportRequest(
        @NotEmpty(message = "Danh sách thành viên nhập không được rỗng")
        @Size(max = 1000, message = "Tối đa 1000 dòng mỗi lần nhập")
        List<@NotNull(message = "Dòng dữ liệu không được null") ImportMemberRowRequest> rows
) {}
