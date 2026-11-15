package com.familyfund.contribution.internal.dto.response;

import java.math.BigDecimal;

/**
 * Standard dynamic VietQR code details for online contributions (US-53).
 */
public record VietQrResponse(
        String qrCodeUrl,
        String qrPayload,
        String bankBin,
        String bankName,
        String accountNumber,
        String accountName,
        BigDecimal amount,
        String transferContent,
        String eventId,
        String eventName,
        String memberId,
        String memberName
) {
}
