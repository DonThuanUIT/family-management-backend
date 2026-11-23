package com.familyfund.finance.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.finance.internal.dto.response.LedgerIntegrityReportResponse;
import com.familyfund.finance.internal.service.ImmutableLedgerIntegrityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only REST Controller allowing clan members and leaders to verify the cryptographic
 * SHA-256 hash-chain integrity of {@code immutable_ledgers} (NFR-01 / US-61).
 */
@RestController
@RequestMapping({"/api/v1/ledger", "/api/ledger"})
@RequiredArgsConstructor
public class ImmutableLedgerIntegrityController {

    private final ImmutableLedgerIntegrityService immutableLedgerIntegrityService;

    /**
     * Kiểm toán toàn vẹn chuỗi mã băm SHA-256 của toàn bộ Sổ cái Quỹ chung thuộc dòng họ hiện tại.
     */
    @GetMapping("/verify-integrity")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LedgerIntegrityReportResponse>> verifyLedgerIntegrity() {
        LedgerIntegrityReportResponse report = immutableLedgerIntegrityService.verifyCurrentClanChain();
        String message = report.valid()
                ? "Chuỗi mã băm SHA-256 Sổ cái Quỹ chung hoàn toàn toàn vẹn"
                : "Cảnh báo bảo mật: Phát hiện sai lệch toàn vẹn chuỗi mã băm SHA-256 trên Sổ cái";
        return ResponseEntity.ok(ApiResponse.success(report, message));
    }
}
