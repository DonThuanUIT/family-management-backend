package com.familyfund.contribution.internal.service;

import com.familyfund.contribution.internal.dto.request.GenerateVietQrRequest;
import com.familyfund.contribution.internal.dto.request.VietQrWebhookPayload;
import com.familyfund.contribution.internal.dto.response.VietQrResponse;
import com.familyfund.contribution.internal.dto.response.WebhookPaymentResultResponse;

/**
 * Service for generating dynamic VietQR codes and handling payment gateway webhooks (US-53 Task #186).
 */
public interface VietQrService {

    /**
     * Generates a dynamic VietQR payment QR code with standardized transfer description (US-53).
     *
     * @param request VietQR generation parameters
     * @return dynamic VietQR details
     */
    VietQrResponse generateVietQr(GenerateVietQrRequest request);

    /**
     * Processes inbound webhook from bank/gateway, verifies transaction, records contribution,
     * and automatically issues an electronic receipt (US-53).
     *
     * @param payload webhook event payload
     * @return processing confirmation
     */
    WebhookPaymentResultResponse processPaymentWebhook(VietQrWebhookPayload payload);
}
