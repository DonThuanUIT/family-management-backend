package com.familyfund.finance.internal.controller;

import com.familyfund.core.dto.ApiResponse;
import com.familyfund.finance.api.dto.LedgerTransactionType;
import com.familyfund.finance.internal.dto.response.LedgerHistoryItemResponse;
import com.familyfund.finance.internal.dto.response.LedgerHistoryPageResponse;
import com.familyfund.finance.internal.service.ImmutableLedgerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only REST Controller for querying the common fund immutable ledger history
 * with bidirectional adjustment links and cash-flow summaries (US-62 / NFR-01).
 *
 * <p>Supports both canonical {@code /api/v1/ledger} and standard {@code /api/ledger} routes.</p>
 */
@RestController
@RequestMapping({"/api/v1/ledger", "/api/ledger"})
@RequiredArgsConstructor
public class ImmutableLedgerQueryController {

    private final ImmutableLedgerQueryService immutableLedgerQueryService;

    /**
     * Tra cứu lịch sử Sổ cái Quỹ chung bất biến có phân trang, lọc theo thời gian/loại giao dịch/sự kiện
     * và liên kết hai chiều giữa giao dịch gốc với các giao dịch bù trừ (US-62).
     */
    @GetMapping({"", "/immutable"})
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LedgerHistoryPageResponse>> searchLedgerHistory(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(name = "transactionType", required = false) List<LedgerTransactionType> transactionTypes,
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean onlyWithAdjustments,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LedgerHistoryPageResponse response = immutableLedgerQueryService.searchLedgerHistory(
                from,
                to,
                transactionTypes,
                eventId,
                keyword,
                onlyWithAdjustments,
                sortDirection,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Tra cứu lịch sử Sổ cái Quỹ chung thành công"));
    }

    /**
     * Xem chi tiết một giao dịch trong Sổ cái Quỹ chung bất biến kèm liên kết hai chiều
     * tới giao dịch gốc hoặc danh sách các giao dịch bù trừ liên quan (US-62).
     */
    @GetMapping("/transactions/{transactionId}")
    @PreAuthorize("hasAnyRole('CLAN_HEAD', 'BRANCH_HEAD', 'MEMBER', 'ADMIN')")
    public ResponseEntity<ApiResponse<LedgerHistoryItemResponse>> getTransactionDetail(
            @PathVariable("transactionId") String transactionId
    ) {
        LedgerHistoryItemResponse response = immutableLedgerQueryService.getTransactionDetail(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết giao dịch Sổ cái Quỹ chung thành công"));
    }
}
