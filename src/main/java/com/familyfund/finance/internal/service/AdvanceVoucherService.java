package com.familyfund.finance.internal.service;

import com.familyfund.finance.api.dto.AdvanceVoucherStatus;
import com.familyfund.finance.internal.dto.request.ConfirmDisbursementRequest;
import com.familyfund.finance.internal.dto.request.CreateAdvanceVoucherRequest;
import com.familyfund.finance.internal.dto.response.AdvanceVoucherResponse;

import java.util.List;

public interface AdvanceVoucherService {

    AdvanceVoucherResponse createAdvanceVoucher(CreateAdvanceVoucherRequest request);

    AdvanceVoucherResponse confirmDisbursement(String advanceVoucherId, ConfirmDisbursementRequest request);

    AdvanceVoucherResponse getAdvanceVoucherById(String advanceVoucherId);

    List<AdvanceVoucherResponse> searchAdvanceVouchers(String eventId, String taskId, String recipientMemberId, AdvanceVoucherStatus status);
}
