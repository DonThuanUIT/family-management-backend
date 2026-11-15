package com.familyfund.notification.api;

import lombok.Builder;

/**
 * Command payload for {@link NotificationService#notify(NotificationCommand)}: one notification for exactly
 * one recipient account.
 *
 * <p>Callers inside the {@code event}, {@code contribution} and future {@code finance} modules build this from
 * their own data; the notification module never reads other modules' tables. {@code referenceType} /
 * {@code referenceId} are a soft pointer back to the business object (for example
 * {@code referenceType = "TASK", referenceId = "TSK..."}) so a client can deep-link from the inbox.</p>
 *
 * @param clanId             tenant that owns the notification ({@code notifications.clan_id})
 * @param recipientAccountId {@code user_accounts.account_id} of the recipient
 * @param category           inbox group (Event / Task / Finance)
 * @param title              short title; truncated to 200 characters by the service
 * @param content            body of the notification (free text, shown in the app)
 * @param referenceType      optional business object type (EVENT, TASK, CONTRIBUTION, ROLE...)
 * @param referenceId        optional business object id
 */
@Builder
public record NotificationCommand(
        String clanId,
        String recipientAccountId,
        NotificationCategory category,
        String title,
        String content,
        String referenceType,
        String referenceId
) {}
