package com.familyfund.identity.internal.notification;

import com.familyfund.identity.api.event.JoinRequestReviewedDomainEvent;

/**
 * Delivery port for the outcome of a join request (US-17).
 *
 * <p>Modelled as an interface with a logging adapter so a real SMS / email / push provider can be plugged in later
 * without touching the approval workflow — the same shape the {@code event} module uses for
 * {@code NotificationSender} + {@code LoggingNotificationSender}.</p>
 */
public interface JoinRequestNotificationSender {

    /**
     * Delivers the review outcome to the applicant.
     *
     * @param event the reviewed join request (recipient + outcome)
     * @param title notification title
     * @param content notification body
     */
    void sendReviewResult(JoinRequestReviewedDomainEvent event, String title, String content);
}