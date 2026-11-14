package com.familyfund.event.api.event;

/**
 * Domain event published when a task progress alert has been persisted and the assignee (or, for the 12h
 * escalation, the branch representative) has been notified (US-08 / US-47).
 *
 * <p>Publishing it lets the notification module write the alert into the recipient's personal inbox without the
 * {@code event} module depending on the notification module: {@code event} only knows this public event.</p>
 *
 * @param clanId              tenant of the event
 * @param eventId             event the task belongs to
 * @param taskId              task that triggered the alert
 * @param taskName            task name, for the notification title
 * @param alertLevel          {@code Reminder24H} (US-08) or {@code OverdueAlert12H} (US-47)
 * @param title               title already rendered by the issuer
 * @param content             body already rendered by the issuer
 * @param recipientMemberId   member id the alert is addressed to (may be null for branch-only tasks)
 * @param recipientAccountId  account that must receive the inbox notification
 */
public record TaskProgressAlertIssuedDomainEvent(
        String clanId,
        String eventId,
        String taskId,
        String taskName,
        String alertLevel,
        String title,
        String content,
        String recipientMemberId,
        String recipientAccountId
) {}
