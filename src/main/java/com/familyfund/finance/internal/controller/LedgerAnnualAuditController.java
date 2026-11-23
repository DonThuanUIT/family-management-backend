package com.familyfund.finance.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.core.exception.BusinessRuleException;
import com.familyfund.finance.api.dto.LedgerAnnualAuditStatus;
import com.familyfund.finance.internal.dto.request.AnnualLedgerAuditRequest;
import com.familyfund.finance.internal.dto.response.LedgerAnnualAuditResponse;
import com.familyfund.finance.internal.service.LedgerAnnualAuditService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.Arrays;
import java.util.List;

/**
 * REST Controller for executing annual common-fund ledger reconciliations
 * and querying annual audit records.
 *
 * <p>Supports both canonical {@code /api/v1/ledger} and standard {@code /api/ledger} routes.</p>
 */
@RestController
@RequestMapping({"/api/v1/ledger", "/api/ledger"})
@RequiredArgsConstructor
public class LedgerAnnualAuditController {

    private final LedgerAnnualAuditService ledgerAnnualAuditService;

    /**
     * Kiểm toán định kỳ hàng năm: đối chiếu số tiền thực kiểm với {@code running_balance} của Sổ cái.
     * Nếu lệch tự động sinh đề xuất bù trừ (US-15); nếu khớp chốt số dư đầu kỳ mới vào Sổ cái bất biến.
     */
    @PostMapping("/annual-audit")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<LedgerAnnualAuditResponse>> performAnnualAudit(
            @Valid @RequestBody AnnualLedgerAuditRequest request
    ) {
        LedgerAnnualAuditResponse response = ledgerAnnualAuditService.performAnnualAudit(request);
        String message = response.auditStatus() == LedgerAnnualAuditStatus.Matched
                ? "Kiểm toán năm " + response.auditYear() + " khớp số dư và đã chốt số dư đầu kỳ mới thành công"
                : "Phát hiện lệch số dư kiểm toán năm " + response.auditYear()
                        + " và đã tự động sinh đề xuất bù trừ (US-15)";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, message));
    }

    /**
     * Tra cứu danh sách biên bản kiểm toán định kỳ Sổ cái Quỹ chung của dòng họ hiện tại.
     */
    @GetMapping("/annual-audits")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<LedgerAnnualAuditResponse>>> searchAnnualAudits(
            @RequestParam(required = false) Integer auditYear,
            @RequestParam(required = false) LedgerAnnualAuditStatus status,
            HttpServletRequest request
    ) {
        rejectDuplicateScalarParam(request, "auditYear");
        rejectDuplicateScalarParam(request, "status");
        List<LedgerAnnualAuditResponse> response = ledgerAnnualAuditService.searchAnnualAudits(auditYear, status);
        return ResponseEntity.ok(ApiResponse.success(
                response,
                "Tra cứu lịch sử kiểm toán định kỳ Sổ cái Quỹ chung thành công"
        ));
    }

    private void rejectDuplicateScalarParam(HttpServletRequest request, String paramName) {
        if (request == null) {
            return;
        }
        String[] values = request.getParameterValues(paramName);
        if (values != null && values.length > 1) {
            throw new BusinessRuleException(
                    "Tham số '" + paramName + "' chỉ chấp nhận một giá trị duy nhất (nhận được "
                            + Arrays.toString(values) + ")");
        }
    }

    /**
     * Xem chi tiết một biên bản kiểm toán định kỳ Sổ cái Quỹ chung theo {@code auditId}.
     */
    @GetMapping("/annual-audits/{auditId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LedgerAnnualAuditResponse>> getAnnualAuditById(
            @PathVariable("auditId") String auditId
    ) {
        LedgerAnnualAuditResponse response = ledgerAnnualAuditService.getAnnualAuditById(auditId);
        return ResponseEntity.ok(ApiResponse.success(
                response,
                "Lấy chi tiết biên bản kiểm toán định kỳ Sổ cái Quỹ chung thành công"
        ));
    }
}
