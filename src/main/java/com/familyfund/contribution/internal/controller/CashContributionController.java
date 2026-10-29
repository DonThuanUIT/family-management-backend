package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.ElectronicReceiptResponse;
import com.familyfund.contribution.internal.dto.RecordCashContributionRequest;
import com.familyfund.contribution.internal.service.CashContributionService;
import com.familyfund.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for cash & bank-transfer contributions and electronic receipts (US-09).
 */
@RestController
@RequestMapping("/api/v1/contributions/cash")
@RequiredArgsConstructor
public class CashContributionController {

    private final CashContributionService cashContributionService;

    /**
     * Tiếp nhận khoản đóng góp bằng tiền mặt hoặc chuyển khoản và cấp phiếu thu điện tử (Task #68, #69, #70).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN') or @nhanSuSuKien.xacThucQuyenTheoNguCanh(#request.eventId(), 'EventTreasurer', 'HeadOfOrganizer')")
    public ResponseEntity<ApiResponse<ElectronicReceiptResponse>> recordCashContribution(
            @Valid @RequestBody RecordCashContributionRequest request) {
        ElectronicReceiptResponse response = cashContributionService.recordCashContribution(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Ghi nhận đóng góp và cấp phiếu thu điện tử thành công"));
    }

    /**
     * Tra cứu chi tiết phiếu thu điện tử theo mã số biên lai (soBienLai).
     */
    @GetMapping("/receipts/{receiptNumber}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ElectronicReceiptResponse>> getReceiptByReceiptNumber(
            @PathVariable String receiptNumber) {
        ElectronicReceiptResponse response = cashContributionService.getReceiptByReceiptNumber(receiptNumber);
        return ResponseEntity.ok(ApiResponse.success(response, "Tra cứu phiếu thu điện tử thành công"));
    }
}
