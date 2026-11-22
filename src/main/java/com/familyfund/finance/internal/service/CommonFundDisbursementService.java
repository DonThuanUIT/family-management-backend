package com.familyfund.finance.internal.service;

import com.familyfund.finance.api.dto.CommonFundDisbursementStatus;
import com.familyfund.finance.api.dto.CommonFundExpenseCategory;
import com.familyfund.finance.internal.dto.request.ProposeCommonFundDisbursementRequest;
import com.familyfund.finance.internal.dto.request.ReviewCommonFundDisbursementRequest;
import com.familyfund.finance.internal.dto.response.CommonFundBalanceResponse;
import com.familyfund.finance.internal.dto.response.CommonFundDisbursementResponse;
import com.familyfund.finance.internal.dto.response.ReviewCommonFundDisbursementResponse;

import java.util.List;

/**
 * Service interface for checking clan common fund balance and managing
 * common fund disbursement proposals and reviews (US-60).
 */
public interface CommonFundDisbursementService {

    /**
     * Returns the current available balance and pending disbursement total of the caller's clan common fund.
     */
    CommonFundBalanceResponse getFundBalance();

    /**
     * Proposes a new common fund disbursement request after validating proposer role and available balance.
     */
    CommonFundDisbursementResponse proposeDisbursement(ProposeCommonFundDisbursementRequest request);

    /**
     * Reviews (approves with password confirmation or rejects with reason) a pending disbursement request.
     */
    ReviewCommonFundDisbursementResponse reviewDisbursement(
            String pathRequestId,
            ReviewCommonFundDisbursementRequest request
    );

    /**
     * Retrieves detailed information of a common fund disbursement request by ID within the caller's clan.
     */
    CommonFundDisbursementResponse getDisbursementById(String requestId);

    /**
     * Searches common fund disbursement requests in the caller's clan by optional status and category filters.
     */
    List<CommonFundDisbursementResponse> searchDisbursements(
            CommonFundDisbursementStatus status,
            CommonFundExpenseCategory category
    );
}
