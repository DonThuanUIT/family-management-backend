package com.familyfund.genealogy.internal.dto.response;

import lombok.Builder;

import java.util.List;

/**
 * Phản hồi của API xác nhận nhập hàng loạt các dòng thành viên hợp lệ (US-31).
 */
@Builder
public record ImportMembersConfirmResponse(
        int insertedCount,
        int skippedCount,
        List<MemberResponse> insertedMembers,
        List<ImportSkippedRow> skippedRows
) {}
