package com.familyfund.genealogy.internal.dto.response;

import lombok.Builder;

import java.util.List;

/**
 * Phản hồi tổng hợp của API xem trước nhập hàng loạt thành viên (US-31).
 */
@Builder
public record ImportMembersPreviewResponse(
        String fileName,
        int totalRows,
        int validCount,
        int errorCount,
        List<ImportMemberPreviewRow> rows
) {}
