package com.familyfund.event.internal.notification;

import com.familyfund.event.api.dto.TaskAlertLevel;

import java.util.List;

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

    /**
     * Sends a direct task assignment notification to designated recipients (members).
     *
     * @param clanId             the tenant clan ID
     * @param eventId            the event ID
     * @param taskId             the task ID
     * @param title              title of the task notification
     * @param content            detailed content of the task assignment
     * @param recipientMemberIds list of recipient member IDs
     */
    void sendTaskAssignedNotification(String clanId, String eventId, String taskId, String title, String content, List<String> recipientMemberIds);

    /**
     * Sends a progress warning for an unfinished task, invoked by the background deadline worker.
     *
     * <p>Two passes use this port:</p>
     * <ul>
     *   <li>{@code Reminder24H} (US-08): {@code recipientMemberId} is the task's {@code assigned_member_id}
     *       (the person who has to finish the work);</li>
     *   <li>{@code OverdueAlert12H} (US-47): {@code recipientMemberId} is the representative of the branch
     *       responsible for the task, i.e. the manager being escalated to — the content names the
     *       assignee and how many hours the task is late.</li>
     * </ul>
     *
     * @param clanId            the tenant clan ID
     * @param eventId           the event ID
     * @param taskId            the task ID
     * @param alertLevel        alert level persisted in `task_progress_alerts`
     * @param title             title of the warning notification
     * @param content           detailed warning content (deadline, remaining/overdue time, assignee)
     * @param recipientMemberId member ID of the recipient (assignee for 24h, branch representative for 12h overdue)
     * @param recipientAccountId user account ID of the recipient
     */
    void sendTaskProgressWarning(String clanId, String eventId, String taskId, TaskAlertLevel alertLevel,
                                 String title, String content, String recipientMemberId, String recipientAccountId);

    /**
     * Sends a direct notification to a member regarding role assignment or revocation (US-03).
     *
     * @param clanId          the tenant clan ID
     * @param targetMemberId  the recipient member ID
     * @param targetAccountId the recipient user account ID (if available)
     * @param roleCode        the role code assigned or revoked
     * @param actionType      ASSIGN or REVOKE
     * @param title           title of the notification
     * @param content         content or explanation of the role change
     */
    void sendRoleChangedNotification(String clanId, String targetMemberId, String targetAccountId, String roleCode, String actionType, String title, String content);
}
