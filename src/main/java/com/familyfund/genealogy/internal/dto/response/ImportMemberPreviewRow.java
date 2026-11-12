package com.familyfund.genealogy.internal.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả kiểm tra từng dòng khi xem trước file Excel/CSV nhập thành viên (US-31).
 */
@Builder
public record ImportMemberPreviewRow(
        int rowNumber,
        boolean valid,
        List<String> errors,
        String memberId,
        String fullName,
        LocalDate dateOfBirth,
        String rawDateOfBirth,
        String gender,
        Integer generation,
        String branchId,
        String branchName,
        String status,
        LocalDate deathDate,
        String rawDeathDate,
        String deathDateLunar,
        String burialPlace
) {}
