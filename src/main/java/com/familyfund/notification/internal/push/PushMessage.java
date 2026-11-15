package com.familyfund.notification.internal.push;

import java.util.Map;

/**
 * One push message as it is delivered to a device (US-39).
 *
 * @param title notification title shown by the OS
 * @param body  notification body shown by the OS
 * @param data  optional data payload for the client (category, referenceType, referenceId, notificationId)
 */
public record PushMessage(String title, String body, Map<String, String> data) {

    public PushMessage {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
