package com.familyfund.notification.api.dto;

/**
 * Payload returned by {@code POST /api/v1/notifications/device-tokens} (US-39).
 *
 * @param tokenId {@code notification_device_tokens.token_id} of the registered token
 * @param platform platform stored for the token
 */
public record DeviceTokenResponse(String tokenId, String platform) {}
