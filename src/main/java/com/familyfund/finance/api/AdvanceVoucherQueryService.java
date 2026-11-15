package com.familyfund.finance.api;

import com.familyfund.finance.api.dto.AdvanceVoucherDto;

import java.util.List;
import java.util.Optional;

/**
 * Public contract for cross-module queries on advance vouchers (US-46).
 */
public interface AdvanceVoucherQueryService {

    Optional<AdvanceVoucherDto> findById(String advanceVoucherId);

    List<AdvanceVoucherDto> findByTaskId(String taskId);

    List<AdvanceVoucherDto> findByEventId(String eventId);
}
