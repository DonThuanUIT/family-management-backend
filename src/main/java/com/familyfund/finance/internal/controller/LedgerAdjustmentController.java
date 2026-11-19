package com.familyfund.finance.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.finance.api.dto.LedgerAdjustmentStatus;
import com.familyfund.finance.internal.dto.request.ProposeLedgerAdjustmentRequest;
import com.familyfund.finance.internal.dto.response.LedgerAdjustmentRequestResponse;
import com.familyfund.finance.internal.service.LedgerAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for proposing and querying ledger adjustment requests (US-15).
 *
 * <p>Supports both canonical {@code /api/v1/ledger} and standard {@code /api/ledger} routes.</p>
 */
@RestController
@RequestMapping({"/api/v1/ledger", "/api/ledger"})
@RequiredArgsConstructor
public class LedgerAdjustmentController {

    private final LedgerAdjustmentService ledgerAdjustmentService;

    /**
     * API đề xuất bù trừ sai sót giao dịch Sổ cái bất biến (LEDGER_ADJUSTMENT_REQUEST).
     */
    @PostMapping("/propose-adjustment")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LedgerAdjustmentRequestResponse>> proposeAdjustment(
            @Valid @RequestBody ProposeLedgerAdjustmentRequest request) {
        LedgerAdjustmentRequestResponse response = ledgerAdjustmentService.proposeAdjustment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Tạo đề xuất điều chỉnh bù trừ sổ cái thành công"));
    }

    /**
     * Tra cứu chi tiết một đề xuất điều chỉnh sổ cái theo requestId.
     */
    @GetMapping({"/adjustments/{requestId}", "/propose-adjustment/{requestId}"})
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LedgerAdjustmentRequestResponse>> getAdjustmentRequestById(
            @PathVariable("requestId") String requestId) {
        LedgerAdjustmentRequestResponse response = ledgerAdjustmentService.getAdjustmentRequestById(requestId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin đề xuất điều chỉnh sổ cái thành công"));
    }

    /**
     * Tra cứu danh sách đề xuất điều chỉnh sổ cái theo dòng họ, lọc theo originalTransactionId hoặc trạng thái.
     */
    @GetMapping({"/adjustments", "/propose-adjustment"})
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<LedgerAdjustmentRequestResponse>>> searchAdjustmentRequests(
            @RequestParam(required = false) String originalTransactionId,
            @RequestParam(required = false) LedgerAdjustmentStatus status) {
        List<LedgerAdjustmentRequestResponse> response =
                ledgerAdjustmentService.searchAdjustmentRequests(originalTransactionId, status);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách đề xuất điều chỉnh sổ cái thành công"));
    }
}
