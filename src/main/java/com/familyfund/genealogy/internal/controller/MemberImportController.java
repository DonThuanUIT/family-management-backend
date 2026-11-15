package com.familyfund.genealogy.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.genealogy.internal.dto.request.ConfirmMemberImportRequest;
import com.familyfund.genealogy.internal.dto.response.ImportMembersConfirmResponse;
import com.familyfund.genealogy.internal.dto.response.ImportMembersPreviewResponse;
import com.familyfund.genealogy.internal.service.MemberImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * REST endpoints cho tính năng nhập hàng loạt thành viên từ file Excel/CSV (US-31).
 */
@RestController
@RequiredArgsConstructor
public class MemberImportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final MemberImportService memberImportService;

    @GetMapping({"/api/v1/members/import-template", "/api/members/import-template"})
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        byte[] content = memberImportService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("mau-nhap-thanh-vien.xlsx", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    @PostMapping(
            value = {"/api/v1/members/import-preview", "/api/members/import-preview"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<ImportMembersPreviewResponse>> previewImport(
            @RequestParam("file") MultipartFile file) {
        ImportMembersPreviewResponse response = memberImportService.previewImport(file);
        return ResponseEntity.ok(ApiResponse.success(response, "Kiểm tra file nhập thành viên hoàn tất"));
    }

    @PostMapping({"/api/v1/members/import-confirm", "/api/members/import-confirm"})
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<ImportMembersConfirmResponse>> confirmImport(
            @Valid @RequestBody ConfirmMemberImportRequest request) {
        ImportMembersConfirmResponse response = memberImportService.confirmImport(request);
        return ResponseEntity.ok(ApiResponse.success(
                response,
                "Đã nhập thành công " + response.insertedCount() + " thành viên hợp lệ"
        ));
    }
}
