package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.request.ReconcileContributionRequest;
import com.familyfund.contribution.internal.dto.request.RecordCashContributionRequest;
import com.familyfund.contribution.internal.dto.response.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.response.ReconcileContributionResponse;
import com.familyfund.contribution.internal.dto.response.UnreconciledContributionResponse;
import com.familyfund.contribution.internal.service.CashContributionService;
import com.familyfund.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for cash & bank-transfer contributions, electronic receipts (US-09),
 * and bank transfer reconciliation (US-50, Task #177).
 */
@RestController
@RequestMapping("/api/v1/contributions/cash")
@RequiredArgsConstructor
public class CashContributionController {

    private final CashContributionService cashContributionService;

    /**
     * Record a cash or bank-transfer contribution and issue an electronic receipt (Task #68, #69, #70).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @nhanSuSuKien.xacThucQuyenTheoNguCanh(#request.eventId(), 'EventTreasurer', 'HeadOfOrganizer')")
    public ResponseEntity<ApiResponse<ElectronicReceiptResponse>> recordCashContribution(
            @Valid @RequestBody RecordCashContributionRequest request) {
        ElectronicReceiptResponse response = cashContributionService.recordCashContribution(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Cash contribution recorded and electronic receipt issued successfully"));
    }

    /**
     * Retrieve an electronic receipt by receipt number.
     */
    @GetMapping("/receipts/{receiptNumber}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ElectronicReceiptResponse>> getReceiptByReceiptNumber(
            @PathVariable String receiptNumber) {
        ElectronicReceiptResponse response = cashContributionService.getReceiptByReceiptNumber(receiptNumber);
        return ResponseEntity.ok(ApiResponse.success(response, "Electronic receipt retrieved successfully"));
    }

    /**
     * Retrieve all cash electronic receipts for a specific event.
     */
    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ElectronicReceiptResponse>>> getCashContributionsByEvent(
            @PathVariable String eventId) {
        List<ElectronicReceiptResponse> response = cashContributionService.getCashContributionsByEvent(eventId);
        return ResponseEntity.ok(ApiResponse.success(response, "Cash contributions retrieved successfully"));
    }

    /**
     * Filter and list bank-transfer contributions not yet matched / reconciled with bank statements (US-50, Task #177).
     */
    @GetMapping({"/unreconciled", "/reconcile/pending"})
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN', 'BRANCH_HEAD', 'MEMBER')")
    public ResponseEntity<ApiResponse<List<UnreconciledContributionResponse>>> getUnreconciledContributions(
            @RequestParam(required = false) String eventId) {
        List<UnreconciledContributionResponse> response = cashContributionService.getUnreconciledContributions(eventId);
        return ResponseEntity.ok(ApiResponse.success(response, "Unreconciled bank transfer contributions retrieved successfully"));
    }

    /**
     * Confirm reconciliation of a bank-transfer contribution with bank statement transaction code & proof (US-50, Task #177).
     */
    @PutMapping("/{contributionId}/reconcile")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN', 'BRANCH_HEAD', 'MEMBER')")
    public ResponseEntity<ApiResponse<ReconcileContributionResponse>> reconcileContribution(
            @PathVariable String contributionId,
            @Valid @RequestBody ReconcileContributionRequest request) {
        ReconcileContributionResponse response = cashContributionService.reconcileContribution(contributionId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Bank transfer contribution reconciled successfully"));
    }
}
