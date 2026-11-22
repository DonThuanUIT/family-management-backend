package com.familyfund.finance.internal.dto.response;

import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Detailed view of a common fund disbursement request, linked to its ledger transaction when approved (US-60).
 */
public record CommonFundDisbursementResponse(
        String requestId,
        String clanId,
        String title,
        CommonFundExpenseCategory category,
        BigDecimal amount,
        String beneficiaryName,
        String beneficiaryMemberId,
        String purpose,
        String attachmentUrl,
        String proposedByAccountId,
        CommonFundDisbursementStatus approvalStatus,
        String reviewedByAccountId,
        String rejectionReason,
        String ledgerTransactionId,
        Instant createdAt,
        Instant reviewedAt
) {}
