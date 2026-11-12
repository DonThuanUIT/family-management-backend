package com.familyfund.genealogy.internal.dto.request;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Một dòng dữ liệu thành viên hợp lệ được gửi từ bước xem trước để xác nhận nhập vào CSDL (US-31).
 */
@Builder
public record ImportMemberRowRequest(
        Integer rowNumber,
        String memberId,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        Integer generation,
        String branchId,
        String status,
        LocalDate deathDate,
        String burialPlace
) {}
