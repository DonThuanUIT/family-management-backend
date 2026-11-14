package com.familyfund.notification.internal.listener;

import com.familyfund.event.api.event.EventOpenedDomainEvent;
import com.familyfund.event.api.event.EventScheduleChangedDomainEvent;
import com.familyfund.event.api.event.RoleChangedDomainEvent;
import com.familyfund.event.api.event.TaskAssignedDomainEvent;
import com.familyfund.event.api.event.TaskProgressAlertIssuedDomainEvent;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns the public domain events of the {@code event} module into rows of the personal inbox (US-39).
 *
 * <p>Why a listener instead of a direct call: the {@code event} module must not depend on the notification module
 * (the notification module already depends on {@code event::api}, so the reverse edge would be a cycle). The
 * listener consumes only {@code event::api} events and resolves recipients through {@code identity::api} /
 * {@code genealogy::api}, keeping the modular boundaries intact.</p>
 *
 * <p>Recipients per event:</p>
 * <ul>
 *   <li><b>Event opened / schedule changed</b> — every active account of the clan (category {@code Event}).</li>
 *   <li><b>Task assigned</b> — the assigned member's account, plus the accounts of all members of the assigned
 *       branch when the task is branch-wide (category {@code Task}).</li>
 *   <li><b>Task progress alert</b> (US-08 24h warning, US-47 12h escalation) — the account that already received
 *       the alert (category {@code Task}).</li>
 *   <li><b>Role changed</b> — the target account (category {@code Event}).</li>
 * </ul>
 *
 * <p>Each notification is written independently: a failure on one recipient is logged and does not stop the
 * fan-out, and the listener never rethrows into the publishing transaction.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notification.inbox.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationInboxListener {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String REFERENCE_EVENT = "EVENT";
    private static final String REFERENCE_TASK = "TASK";
    private static final String REFERENCE_ROLE = "ROLE";

    private final NotificationService notificationService;
    private final IdentityRoleCommandService identityRoleCommandService;
    private final GenealogyQueryService genealogyQueryService;

    /** An event opened for registration: broadcast to the whole clan. */
    @Async
    @EventListener
    public void onEventOpened(EventOpenedDomainEvent event) {
        if (event == null || isBlank(event.clanId())) {
            return;
        }
        String eventName = safeName(event.eventName(), event.eventId());
        String title = "Sự kiện mới: " + eventName;
        StringBuilder content = new StringBuilder("Sự kiện '").append(eventName).append("' đã mở đăng ký");
        if (event.eventDatetime() != null) {
            content.append(", diễn ra lúc ").append(DATE_FORMAT.format(event.eventDatetime()));
        }
        if (!isBlank(event.location())) {
            content.append(" tại ").append(event.location().trim());
        }
        content.append('.');
        broadcastToClan(event.clanId(), NotificationCategory.Event, title, content.toString(),
                REFERENCE_EVENT, event.eventId());
    }

    /** An event's schedule or location changed: broadcast to the whole clan. */
    @Async
    @EventListener
    public void onEventScheduleChanged(EventScheduleChangedDomainEvent event) {
        if (event == null || isBlank(event.clanId())) {
            return;
        }
        String eventName = safeName(event.eventName(), event.eventId());
        String title = "Thay đổi lịch trình: " + eventName;
        String content = isBlank(event.contentChanges())
                ? "Sự kiện '" + eventName + "' vừa được cập nhật lịch trình."
                : event.contentChanges().trim();
        broadcastToClan(event.clanId(), NotificationCategory.Event, title, content,
                REFERENCE_EVENT, event.eventId());
    }

    /** A task was assigned to a member and/or a branch. */
    @Async
    @EventListener
    public void onTaskAssigned(TaskAssignedDomainEvent event) {
        if (event == null || isBlank(event.clanId()) || isBlank(event.taskId())) {
            return;
        }
        Set<String> accountIds = new LinkedHashSet<>();

        if (!isBlank(event.assignedMemberId())) {
            identityRoleCommandService.findAccountByMemberId(event.assignedMemberId().trim())
                    .map(AccountSummaryDto::accountId)
                    .ifPresent(accountIds::add);
        }
        if (!isBlank(event.assignedBranchId())) {
            List<String> branchMemberIds = genealogyQueryService
                    .getMemberIdsByBranch(event.clanId().trim(), event.assignedBranchId().trim());
            if (branchMemberIds != null && !branchMemberIds.isEmpty()) {
                identityRoleCommandService.findAccountsByMemberIds(branchMemberIds).values().stream()
                        .map(AccountSummaryDto::accountId)
                        .forEach(accountIds::add);
            }
        }
        if (accountIds.isEmpty()) {
            log.debug("US-39: task [{}] has no account to notify", event.taskId());
            return;
        }

        String taskName = safeName(event.taskName(), event.taskId());
        String eventName = safeName(event.eventName(), event.eventId());
        String title = "Bạn được giao việc: " + taskName;
        StringBuilder content = new StringBuilder("Sự kiện '").append(eventName)
                .append("': công việc '").append(taskName).append("'");
        if (event.deadline() != null) {
            content.append(", hạn chót ").append(DATE_FORMAT.format(event.deadline()));
        }
        content.append('.');

        accountIds.forEach(accountId -> notifyQuietly(NotificationCommand.builder()
                .clanId(event.clanId().trim())
                .recipientAccountId(accountId)
                .category(NotificationCategory.Task)
                .title(title)
                .content(content.toString())
                .referenceType(REFERENCE_TASK)
                .referenceId(event.taskId())
                .build()));
    }

    /** A 24h progress warning (US-08) or a 12h overdue escalation (US-47) was issued. */
    @Async
    @EventListener
    public void onTaskProgressAlertIssued(TaskProgressAlertIssuedDomainEvent event) {
        if (event == null || isBlank(event.clanId()) || isBlank(event.recipientAccountId())) {
            return;
        }
        notifyQuietly(NotificationCommand.builder()
                .clanId(event.clanId().trim())
                .recipientAccountId(event.recipientAccountId().trim())
                .category(NotificationCategory.Task)
                .title(isBlank(event.title()) ? "Cảnh báo tiến độ công việc" : event.title().trim())
                .content(isBlank(event.content()) ? "Công việc '" + safeName(event.taskName(), event.taskId()) + "'"
                        : event.content().trim())
                .referenceType(REFERENCE_TASK)
                .referenceId(event.taskId())
                .build());
    }

    /** A role was assigned or revoked. */
    @Async
    @EventListener
    public void onRoleChanged(RoleChangedDomainEvent event) {
        if (event == null || isBlank(event.clanId())) {
            return;
        }
        String accountId = !isBlank(event.targetAccountId())
                ? event.targetAccountId().trim()
                : identityRoleCommandService.findAccountByMemberId(event.targetMemberId())
                        .map(AccountSummaryDto::accountId)
                        .orElse(null);
        if (isBlank(accountId)) {
            log.debug("US-39: role change [{}] has no linked account to notify", event.auditId());
            return;
        }

        String roleCode = event.roleCode() == null ? "" : event.roleCode().name();
        String action = event.actionType() == null ? "" : event.actionType().name().toUpperCase(Locale.ROOT);
        String title = "Vai trò của bạn đã thay đổi";
        String content = "Vai trò " + roleCode + (action.contains("REVOKE") ? " đã bị thu hồi" : " vừa được gán")
                + (isBlank(event.scopeName()) ? "." : " cho '" + event.scopeName().trim() + "'.");

        notifyQuietly(NotificationCommand.builder()
                .clanId(event.clanId().trim())
                .recipientAccountId(accountId)
                .category(NotificationCategory.Event)
                .title(title)
                .content(content)
                .referenceType(REFERENCE_ROLE)
                .referenceId(event.targetMemberId())
                .build());
    }

    // ------------------------------------------------------------------ helpers

    private void broadcastToClan(String clanId, NotificationCategory category, String title, String content,
                                 String referenceType, String referenceId) {
        List<AccountSummaryDto> accounts = identityRoleCommandService.findActiveAccountsByClan(clanId.trim());
        if (accounts.isEmpty()) {
            log.debug("US-39: clan [{}] has no active account to notify", clanId);
            return;
        }
        accounts.forEach(account -> notifyQuietly(NotificationCommand.builder()
                .clanId(clanId.trim())
                .recipientAccountId(account.accountId())
                .category(category)
                .title(title)
                .content(content)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build()));
    }

    private void notifyQuietly(NotificationCommand command) {
        try {
            notificationService.notify(command);
        } catch (RuntimeException ex) {
            log.warn("US-39: cannot persist notification for account {}: {}",
                    command.recipientAccountId(), ex.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeName(String name, String fallback) {
        return isBlank(name) ? (fallback == null ? "" : fallback) : name.trim();
    }
}
