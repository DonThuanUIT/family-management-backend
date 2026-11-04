package com.familyfund.event.internal.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request payload for creating a preparatory task for an event (US-07).
 */
public record CreateTaskRequest(
        @NotBlank(message = "Tên công việc không được để trống")
        @Size(max = 100, message = "Tên công việc tối đa 100 ký tự")
        String taskName,

        @Size(max = 4000, message = "Nội dung chi tiết công việc tối đa 4000 ký tự")
        String details,

        @DecimalMin(value = "0.0", message = "Ngân sách tạm ứng dự kiến phải lớn hơn hoặc bằng 0")
        BigDecimal advanceBudget,

        @NotNull(message = "Hạn chót công việc không được để trống")
        @Future(message = "Hạn chót công việc phải là thời điểm trong tương lai")
        Instant deadline,

        @Size(max = 20, message = "Mã chi nhánh tối đa 20 ký tự")
        String assignedBranchId,

        @Size(max = 20, message = "Mã thành viên tối đa 20 ký tự")
        String assignedMemberId
) {
    public CreateTaskRequest {
        if (advanceBudget == null) {
            advanceBudget = BigDecimal.ZERO;
        }
    }
}

