package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.response.MemberContributionHistoryResponse;
import com.familyfund.contribution.internal.service.ContributionHistoryService;
import com.familyfund.core.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for member contribution history and electronic receipt PDF downloads (US-49, Task #174).
 */
@RestController
@RequestMapping({"/api/v1/contributions", "/api/contributions"})
@RequiredArgsConstructor
public class ContributionHistoryController {

    private final ContributionHistoryService contributionHistoryService;

    /**
     * Retrieve personal and household contribution history (Task #174).
     */
    @GetMapping("/my-history")
    @PreAuthorize("hasAnyRole('MEMBER', 'BRANCH_HEAD', 'CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<MemberContributionHistoryResponse>> getMyContributionHistory(
            @RequestParam(defaultValue = "false") boolean includeFamily) {
        MemberContributionHistoryResponse response = contributionHistoryService.getMyContributionHistory(includeFamily);
        return ResponseEntity.ok(ApiResponse.success(response, "Contribution history retrieved successfully"));
    }

    /**
     * Download an electronic receipt in PDF format (Task #174).
     */
    @GetMapping("/receipts/{receiptNumber}/pdf")
    @PreAuthorize("hasAnyRole('MEMBER', 'BRANCH_HEAD', 'CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<byte[]> downloadReceiptPdf(@PathVariable String receiptNumber) {
        byte[] pdfBytes = contributionHistoryService.downloadReceiptPdf(receiptNumber);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "receipt_" + receiptNumber + ".pdf");
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
