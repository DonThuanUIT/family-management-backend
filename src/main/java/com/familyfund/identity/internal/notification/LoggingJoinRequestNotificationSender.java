package com.familyfund.identity.internal.notification;

import com.familyfund.identity.api.event.JoinRequestReviewedDomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Log-backed {@link JoinRequestNotificationSender}.
 *
 * <p>The project currently has no SMS / email provider wired in, so the notification is written to the application
 * log with the delivery address and the exact body a real gateway would receive. Swapping in a provider means
 * adding another {@code JoinRequestNotificationSender} bean, not changing the approval workflow.</p>
 */
@Slf4j
@Component
public class LoggingJoinRequestNotificationSender implements JoinRequestNotificationSender {

    @Override
    public void sendReviewResult(JoinRequestReviewedDomainEvent event, String title, String content) {
        log.info("""
                        [JOIN-REQUEST NOTIFICATION]
                        to={} ({})
                        requestId={}
                        title={}
                        content={}
                        """,
                event.phoneNumber(),
                event.fullName(),
                event.requestId(),
                title,
                content);
    }
}