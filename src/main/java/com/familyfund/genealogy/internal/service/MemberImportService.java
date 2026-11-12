package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.internal.dto.request.ConfirmMemberImportRequest;
import com.familyfund.genealogy.internal.dto.response.ImportMembersConfirmResponse;
import com.familyfund.genealogy.internal.dto.response.ImportMembersPreviewResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nghiệp vụ tải file mẫu, xem trước và xác nhận nhập hàng loạt thành viên từ Excel/CSV (US-31).
 */
public interface MemberImportService {

    byte[] generateTemplate();

    ImportMembersPreviewResponse previewImport(MultipartFile file);

    ImportMembersConfirmResponse confirmImport(ConfirmMemberImportRequest request);
}
