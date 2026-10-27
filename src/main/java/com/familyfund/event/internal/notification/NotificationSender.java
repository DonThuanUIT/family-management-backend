package com.familyfund.event.internal.notification;

/**
 * Interface contract for broadcasting event notifications to clan members.
 */
public interface NotificationSender {

    /**
     * Broadcasts an announcement message to all members/accounts of a clan.
     *
     * @param clanId   the tenant clan ID
     * @param eventId  the event ID
     * @param title    title of the notification
     * @param content  detailed content or list of schedule changes
     */
    void sendEventBroadcast(String clanId, String eventId, String title, String content);
}
