package com.familyfund.finance.internal.dto.request;

import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request payload for confirming cash disbursement by the event treasurer (US-46).
 */
public record ConfirmDisbursementRequest(
        @Size(max = 1000, message = "Ghi chú giao tiền tối đa 1000 ký tự")
        String notes,

        Instant disbursedAt
) {}
