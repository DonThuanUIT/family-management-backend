package com.familyfund.genealogy.internal.service;

import java.time.LocalDate;

/**
 * Dòng dữ liệu thô đọc được từ file Excel hoặc CSV trước khi kiểm tra nghiệp vụ (US-31).
 */
record ParsedMemberRow(
        int rowNumber,
        String memberId,
        String fullName,
        String rawDateOfBirth,
        LocalDate nativeDateOfBirth,
        String gender,
        String rawGeneration,
        String branchId,
        String status,
        String rawDeathDate,
        LocalDate nativeDeathDate,
        String burialPlace
) {}
