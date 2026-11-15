package com.familyfund.finance.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.finance.api.AdvanceVoucherOverdueService;
import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.api.dto.OverdueScanSummary;
import com.familyfund.finance.internal.dto.request.ConfirmDisbursementRequest;
import com.familyfund.finance.internal.dto.request.CreateAdvanceVoucherRequest;
import com.familyfund.finance.internal.dto.response.AdvanceVoucherResponse;
import com.familyfund.finance.internal.service.AdvanceVoucherService;
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

import java.time.Instant;
import java.util.List;

/**
 * REST Controller for managing event advance vouchers (US-46, US-48).
 *
 * <p>Supports both canonical {@code /api/v1/advances} and standard {@code /api/advances} routes.</p>
 */
@RestController
@RequestMapping({"/api/v1/advances", "/api/advances"})
@RequiredArgsConstructor
public class AdvanceVoucherController {

    private final AdvanceVoucherService advanceVoucherService;
    private final AdvanceVoucherOverdueService advanceVoucherOverdueService;

    /**
     * API cho BTC lập phiếu tạm ứng kinh phí (US-46).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN', 'MEMBER')")
    public ResponseEntity<ApiResponse<AdvanceVoucherResponse>> createAdvanceVoucher(
            @Valid @RequestBody CreateAdvanceVoucherRequest request) {
        AdvanceVoucherResponse response = advanceVoucherService.createAdvanceVoucher(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Lập phiếu tạm ứng thành công"));
    }

    /**
     * API cho Thủ quỹ xác nhận đã giao tiền, đánh dấu trạng thái ChuaTatToan (US-46).
     */
    @PostMapping({"/{id}/disburse", "/{id}/confirm-disbursement", "/{id}/confirm"})
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN', 'MEMBER')")
    public ResponseEntity<ApiResponse<AdvanceVoucherResponse>> confirmDisbursement(
            @PathVariable("id") String id,
            @Valid @RequestBody(required = false) ConfirmDisbursementRequest request) {
        AdvanceVoucherResponse response = advanceVoucherService.confirmDisbursement(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Thủ quỹ đã xác nhận giao tiền tạm ứng (ChuaTatToan) thành công"));
    }

    /**
     * Tra cứu chi tiết một phiếu tạm ứng theo ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AdvanceVoucherResponse>> getAdvanceVoucherById(
            @PathVariable("id") String id) {
        AdvanceVoucherResponse response = advanceVoucherService.getAdvanceVoucherById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin phiếu tạm ứng thành công"));
    }

    /**
     * Danh sách / tìm kiếm phiếu tạm ứng theo sự kiện, công việc, người nhận hoặc trạng thái.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AdvanceVoucherResponse>>> searchAdvanceVouchers(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String recipientMemberId,
            @RequestParam(required = false) AdvanceVoucherStatus status) {
        List<AdvanceVoucherResponse> response = advanceVoucherService.searchAdvanceVouchers(eventId, taskId, recipientMemberId, status);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách phiếu tạm ứng thành công"));
    }

    /**
     * API quét và cập nhật các phiếu tạm ứng quá hạn quyết toán sang QuaHan (US-48).
     */
    @PostMapping("/scan-overdue")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<OverdueScanSummary>> scanOverdueVouchers() {
        OverdueScanSummary summary = advanceVoucherOverdueService.scanAndProcessOverdueVouchers(Instant.now());
        return ResponseEntity.ok(ApiResponse.success(summary, "Quét và xử lý phiếu tạm ứng quá hạn thành công"));
    }
}
