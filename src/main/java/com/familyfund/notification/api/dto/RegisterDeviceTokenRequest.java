package com.familyfund.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Payload of {@code POST /api/v1/notifications/device-tokens}: registers the FCM registration token of the
 * caller's mobile/web device so push notifications can be delivered to it (US-39).
 *
 * @param fcmToken Firebase Cloud Messaging registration token obtained by the client SDK
 * @param platform device platform, matching {@code notification_device_tokens.platform} CHECK constraint
 */
@Builder
public record RegisterDeviceTokenRequest(

        // fcm_token VARCHAR(255) NOT NULL UNIQUE
        @NotBlank(message = "FCM token must not be blank")
        @Size(max = 255, message = "FCM token must not exceed 255 characters")
        String fcmToken,

        // platform VARCHAR(20) NOT NULL CHECK (platform IN ('Android', 'IOS', 'Web'))
        @Pattern(regexp = "Android|IOS|Web", message = "Platform must be one of: Android, IOS, Web")
        String platform
) {}
