package com.familyfund.notification.internal.push;

import java.util.List;

/**
 * Delivery port for mobile/web push (US-39).
 *
 * <p>Two adapters exist:</p>
 * <ul>
 *   <li>{@link LoggingPushNotificationSender} — the default. Writes the message to the application log so the
 *       feature works with no Firebase project configured (local dev, tests, CI).</li>
 *   <li>{@link FcmPushNotificationSender} — Firebase Cloud Messaging HTTP v1, enabled with
 *       {@code app.notification.push.provider=fcm}.</li>
 * </ul>
 *
 * <p>A push failure must never break the inbox: the notification row is already persisted when this port is
 * called, so implementations return the tokens that are no longer valid instead of throwing.</p>
 */
public interface PushNotificationSender {

    /**
     * Delivers one message to every given device token.
     *
     * @param deviceTokens FCM registration tokens (never null; may be empty)
     * @param message      title / body / data payload
     * @return tokens Firebase reported as permanently invalid; the caller deactivates them. Empty when the
     *         adapter cannot tell (for example the logging adapter).
     */
    List<String> send(List<String> deviceTokens, PushMessage message);

    /**
     * @return {@code true} when this adapter can actually reach a push provider; when {@code false} the caller
     *         skips the push entirely (the notification is still persisted).
     */
    boolean isEnabled();

    /** Short name used in logs. */
    String providerName();
}
