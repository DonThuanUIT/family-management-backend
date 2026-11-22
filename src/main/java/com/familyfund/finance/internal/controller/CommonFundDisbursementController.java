package com.familyfund.finance.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.internal.dto.request.ProposeCommonFundDisbursementRequest;
import com.familyfund.finance.internal.dto.request.ReviewCommonFundDisbursementRequest;
import com.familyfund.finance.internal.dto.response.CommonFundBalanceResponse;
import com.familyfund.finance.internal.dto.response.CommonFundDisbursementResponse;
import com.familyfund.finance.internal.dto.response.ReviewCommonFundDisbursementResponse;
import com.familyfund.finance.internal.service.CommonFundDisbursementService;
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
 * REST Controller for clan common fund balance lookup, disbursement proposals,
 * and Clan Head approvals with password confirmation (US-60).
 *
 * <p>Supports both canonical {@code /api/v1/ledger} and standard {@code /api/ledger} routes.</p>
 */
@RestController
@RequestMapping({"/api/v1/ledger", "/api/ledger"})
@RequiredArgsConstructor
public class CommonFundDisbursementController {

    private final CommonFundDisbursementService commonFundDisbursementService;

    /**
     * Xem số dư khả dụng hiện tại của Quỹ chung dòng họ và tổng tiền các phiếu chi đang chờ duyệt.
     */
    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CommonFundBalanceResponse>> getFundBalance() {
        CommonFundBalanceResponse response = commonFundDisbursementService.getFundBalance();
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy số dư khả dụng Quỹ chung thành công"));
    }

    /**
     * Đề xuất chi Quỹ chung cho các việc lớn (tu sửa từ đường, cứu trợ, khuyến học, nghi lễ...).
     */
    @PostMapping("/disbursements")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CommonFundDisbursementResponse>> proposeDisbursement(
            @Valid @RequestBody ProposeCommonFundDisbursementRequest request) {
        CommonFundDisbursementResponse response = commonFundDisbursementService.proposeDisbursement(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Tạo đề xuất chi Quỹ chung thành công"));
    }

    /**
     * Trưởng tộc / Admin phê duyệt (kèm xác thực mật khẩu) hoặc từ chối đề xuất chi Quỹ chung theo path requestId.
     */
    @PostMapping("/disbursements/{requestId}/review")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReviewCommonFundDisbursementResponse>> reviewDisbursementByPath(
            @PathVariable("requestId") String requestId,
            @Valid @RequestBody ReviewCommonFundDisbursementRequest request) {
        return buildReviewResponse(requestId, request);
    }

    /**
     * Route phẳng tương đương {@code /disbursements/{requestId}/review} dành cho client truyền {@code requestId} trong body.
     */
    @PostMapping("/review-disbursement")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<ReviewCommonFundDisbursementResponse>> reviewDisbursement(
            @Valid @RequestBody ReviewCommonFundDisbursementRequest request) {
        return buildReviewResponse(null, request);
    }

    /**
     * Tra cứu danh sách đề xuất chi Quỹ chung theo dòng họ, lọc theo trạng thái và danh mục.
     */
    @GetMapping("/disbursements")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<CommonFundDisbursementResponse>>> searchDisbursements(
            @RequestParam(required = false) CommonFundDisbursementStatus status,
            @RequestParam(required = false) CommonFundExpenseCategory category) {
        List<CommonFundDisbursementResponse> response =
                commonFundDisbursementService.searchDisbursements(status, category);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách đề xuất chi Quỹ chung thành công"));
    }

    /**
     * Xem chi tiết một đề xuất chi Quỹ chung theo requestId.
     */
    @GetMapping("/disbursements/{requestId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CommonFundDisbursementResponse>> getDisbursementById(
            @PathVariable("requestId") String requestId) {
        CommonFundDisbursementResponse response = commonFundDisbursementService.getDisbursementById(requestId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy thông tin đề xuất chi Quỹ chung thành công"));
    }

    private ResponseEntity<ApiResponse<ReviewCommonFundDisbursementResponse>> buildReviewResponse(
            String pathRequestId,
            ReviewCommonFundDisbursementRequest request) {
        ReviewCommonFundDisbursementResponse response =
                commonFundDisbursementService.reviewDisbursement(pathRequestId, request);
        String message = Boolean.TRUE.equals(request.approved())
                ? "Phê duyệt chi Quỹ chung và ghi bút toán vào sổ cái bất biến thành công"
                : "Từ chối đề xuất chi Quỹ chung thành công";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }
}
