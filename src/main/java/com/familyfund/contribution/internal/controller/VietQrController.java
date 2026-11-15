package com.familyfund.contribution.internal.controller;

import com.familyfund.contribution.internal.dto.request.GenerateVietQrRequest;
import com.familyfund.contribution.internal.dto.request.VietQrWebhookPayload;
import com.familyfund.contribution.internal.dto.response.VietQrResponse;
import com.familyfund.contribution.internal.dto.response.WebhookPaymentResultResponse;
import com.familyfund.contribution.internal.service.VietQrService;
import com.familyfund.core.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for VietQR online payment generation and payment webhook ingestion (US-53 Task #186).
 */
@RestController
@RequiredArgsConstructor
public class VietQrController {

    private final VietQrService vietQrService;

    /**
     * Generate dynamic VietQR code with standardized transfer description (Task #186).
     */
    @PostMapping({"/api/v1/contributions/vietqr/generate", "/api/contributions/vietqr/generate"})
    @PreAuthorize("hasAnyRole('MEMBER', 'BRANCH_HEAD', 'CLAN_HEAD', 'ADMIN')")
    public ResponseEntity<ApiResponse<VietQrResponse>> generateVietQr(
            @Valid @RequestBody GenerateVietQrRequest request) {
        VietQrResponse response = vietQrService.generateVietQr(request);
        return ResponseEntity.ok(ApiResponse.success(response, "VietQR dynamic payment code generated successfully"));
    }

    /**
     * Inbound payment webhook to automatically record bank transfer contribution and issue receipt (Task #186).
     */
    @PostMapping({"/api/v1/contributions/vietqr/webhook", "/api/contributions/vietqr/webhook"})
    public ResponseEntity<ApiResponse<WebhookPaymentResultResponse>> receivePaymentWebhook(
            @RequestBody VietQrWebhookPayload payload) {
        WebhookPaymentResultResponse response = vietQrService.processPaymentWebhook(payload);
        return ResponseEntity.ok(ApiResponse.success(response, response.message()));
    }
}
