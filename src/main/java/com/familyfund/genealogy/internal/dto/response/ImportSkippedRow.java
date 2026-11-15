package com.familyfund.genealogy.internal.dto.response;

import lombok.Builder;

import java.util.List;

/**
 * Thông tin dòng bị bỏ qua trong bước xác nhận nhập hàng loạt (US-31).
 */
@Builder
public record ImportSkippedRow(
        int rowNumber,
        String fullName,
        List<String> errors
) {}
