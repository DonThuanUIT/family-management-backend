package com.familyfund.finance.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.core.storage.MediaException;
import com.familyfund.finance.internal.dto.response.LedgerEvidenceUploadResponse;
import com.familyfund.finance.internal.service.LedgerEvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * REST Controller for uploading and securely streaming supporting evidence files
 * attached to ledger adjustment proposals (US-15).
 *
 * <p>Supports both canonical {@code /api/v1/ledger} and standard {@code /api/ledger} routes.</p>
 */
@RestController
@RequestMapping({"/api/v1/ledger", "/api/ledger"})
@RequiredArgsConstructor
public class LedgerEvidenceController {

    private final LedgerEvidenceService ledgerEvidenceService;

    /**
     * Tải lên biên bản/chứng từ (PDF, JPG, PNG tối đa 10 MB) vào kho lưu trữ riêng tư
     * và trả về đường dẫn proxy nội bộ để gắn vào đề xuất bù trừ sổ cái (US-15).
     */
    @PostMapping(value = "/evidence-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LedgerEvidenceUploadResponse>> uploadEvidence(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "originalTransactionId", required = false) String originalTransactionId
    ) {
        LedgerEvidenceUploadResponse response =
                ledgerEvidenceService.uploadEvidence(file, originalTransactionId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Tải lên biên bản chứng từ thành công"));
    }

    /**
     * Truy xuất nội dung file biên bản từ kho lưu trữ riêng tư của dòng họ hiện tại.
     */
    @GetMapping("/evidence/{fileName}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<InputStreamResource> downloadEvidence(
            @PathVariable("fileName") String fileName,
            @RequestParam(defaultValue = "inline") String disposition
    ) {
        if (!"inline".equals(disposition) && !"attachment".equals(disposition)) {
            throw MediaException.invalid("Cách hiển thị tệp không hợp lệ");
        }
        LedgerEvidenceService.EvidenceDownload download = ledgerEvidenceService.downloadEvidence(fileName);
        boolean allowInline = "inline".equals(disposition)
                && (download.mimeType().startsWith("image/") || "application/pdf".equals(download.mimeType()));
        ContentDisposition contentDisposition = (allowInline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.stream()));
    }
}
