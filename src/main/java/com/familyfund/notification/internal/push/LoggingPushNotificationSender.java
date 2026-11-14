package com.familyfund.notification.internal.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link PushNotificationSender}: writes the push payload to the application log (US-39).
 *
 * <p>Selected when {@code app.notification.push.provider} is absent or {@code log}. It keeps the inbox fully
 * functional without a Firebase project — the row is persisted by {@code NotificationService} either way — and it
 * is what the automated tests and local development run on.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.notification.push.provider", havingValue = "log", matchIfMissing = true)
public class LoggingPushNotificationSender implements PushNotificationSender {

    @Override
    public List<String> send(List<String> deviceTokens, PushMessage message) {
        if (deviceTokens == null || deviceTokens.isEmpty()) {
            return List.of();
        }
        log.info("[ThongBao Push] provider=log devices={} title=\"{}\" body=\"{}\" data={}",
                deviceTokens.size(), message.title(), message.body(), message.data());
        return List.of();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String providerName() {
        return "log";
    }
}
