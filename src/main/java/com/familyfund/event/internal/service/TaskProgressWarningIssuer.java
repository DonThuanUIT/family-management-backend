package com.familyfund.event.internal.service;

import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.TaskAlertLevel;
import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventTask;
import com.familyfund.event.internal.entity.TaskProgressAlert;
import com.familyfund.event.internal.notification.NotificationSender;
import com.familyfund.event.api.event.TaskProgressAlertIssuedDomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.event.internal.repository.TaskProgressAlertRepository;
import com.familyfund.event.internal.util.AlertIdGenerator;
import com.familyfund.identity.api.IdentityRoleCommandService;
import com.familyfund.identity.api.dto.AccountSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Issues a single 24-hour {@code ProgressWarning} for one unfinished event task (US-08).
 *
 * <p>Extracted from {@link TaskProgressAlertServiceImpl} so that {@link Transactional} is applied
 * through the Spring proxy (one transaction per task). The alert log row is written <b>before</b>
 * the notification is dispatched: if the sender fails, the transaction rolls back and the task is
 * retried on the next scan pass instead of being silently marked as already warned.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProgressWarningIssuer {

    /** Deadline proximity that triggers a {@code ProgressWarning} (US-08: 24 hours or less). */
    public static final Duration WARNING_WINDOW = Duration.ofHours(24);

    private static final int MAX_TITLE_LENGTH = 200;

    private final EventTaskRepository eventTaskRepository;
    private final EventRepository eventRepository;
    private final TaskProgressAlertRepository taskProgressAlertRepository;
    private final AlertIdGenerator alertIdGenerator;
    private final IdentityRoleCommandService identityRoleCommandService;
    private final NotificationSender notificationSender;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Evaluates one task and, when all US-08 conditions hold, persists the progress alert and
     * notifies the assigned member.
     *
     * @param taskId ID of the candidate task
     * @param now    reference instant shared by the whole scan pass
     * @return {@code true} when a new progress warning was issued, {@code false} when skipped
     */
    @Transactional
    public boolean issueProgressWarning(String taskId, Instant now) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        Instant referenceInstant = (now != null) ? now : Instant.now();

        EventTask task = eventTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.debug("US-08: task [{}] no longer exists, skipping", taskId);
            return false;
        }

        if (!isWarningCandidate(task, referenceInstant)) {
            return false;
        }

        // Idempotency: one Reminder24H warning per task.
        if (taskProgressAlertRepository.existsByTaskIdAndAlertLevel(task.getTaskId(), TaskAlertLevel.Reminder24H)) {
            log.debug("US-08: task [{}] already received a 24h progress warning, skipping", task.getTaskId());
            return false;
        }

        Event event = eventRepository.findById(task.getEventId()).orElse(null);
        if (event == null || event.getStatus() == EventStatus.Closed) {
            log.debug("US-08: event [{}] of task [{}] is closed or missing, skipping", task.getEventId(), task.getTaskId());
            return false;
        }

        String memberId = task.getAssignedMemberId().trim();
        Optional<AccountSummaryDto> account = identityRoleCommandService.findAccountByMemberId(memberId);
        if (account.isEmpty()) {
            // `task_progress_alerts.recipient_account_id` is NOT NULL: no account means no channel.
            log.warn("US-08: assigned member [{}] of task [{}] has no linked user account, cannot warn",
                    memberId, task.getTaskId());
            return false;
        }

        String title = buildTitle(task);
        String content = buildContent(task, event, referenceInstant);

        TaskProgressAlert alert = TaskProgressAlert.builder()
                .alertId(alertIdGenerator.generate())
                .taskId(task.getTaskId())
                .alertLevel(TaskAlertLevel.Reminder24H)
                .recipientAccountId(account.get().accountId())
                .alertContent(content)
                .sentTime(referenceInstant)
                .isRead(Boolean.FALSE)
                .build();

        taskProgressAlertRepository.save(alert);

        notificationSender.sendTaskProgressWarning(
                event.getClanId(),
                event.getEventId(),
                task.getTaskId(),
                TaskAlertLevel.Reminder24H,
                title,
                content,
                memberId,
                account.get().accountId()
        );

        // US-39: let the notification module mirror this alert into the recipient's personal inbox.
        applicationEventPublisher.publishEvent(new TaskProgressAlertIssuedDomainEvent(
                event.getClanId(),
                event.getEventId(),
                task.getTaskId(),
                task.getTaskName(),
                TaskAlertLevel.Reminder24H.name(),
                title,
                content,
                memberId,
                account.get().accountId()
        ));

        log.info("US-08: progress warning [{}] issued for task [{}] (deadline {}) to member [{}]",
                alert.getAlertId(), task.getTaskId(), task.getDeadline(), memberId);
        return true;
    }

    private boolean isWarningCandidate(EventTask task, Instant now) {
        if (task.getStatus() == TaskStatus.Completed) {
            log.debug("US-08: task [{}] is completed, skipping", task.getTaskId());
            return false;
        }
        if (task.getAssignedMemberId() == null || task.getAssignedMemberId().isBlank()) {
            log.debug("US-08: task [{}] has no assigned member, nothing to notify", task.getTaskId());
            return false;
        }
        Instant deadline = task.getDeadline();
        if (deadline == null || !deadline.isAfter(now) || deadline.isAfter(now.plus(WARNING_WINDOW))) {
            log.debug("US-08: task [{}] deadline [{}] is outside the 24h warning window", task.getTaskId(), deadline);
            return false;
        }
        return true;
    }

    private String buildTitle(EventTask task) {
        String taskName = (task.getTaskName() != null && !task.getTaskName().isBlank())
                ? task.getTaskName().trim()
                : task.getTaskId();
        String rawTitle = "Cảnh báo tiến độ: " + taskName;
        return rawTitle.length() > MAX_TITLE_LENGTH
                ? rawTitle.substring(0, MAX_TITLE_LENGTH - 3) + "..."
                : rawTitle;
    }

    private String buildContent(EventTask task, Event event, Instant now) {
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("Công việc '").append(task.getTaskName()).append("'");
        if (event.getEventName() != null && !event.getEventName().isBlank()) {
            contentBuilder.append(" của sự kiện '").append(event.getEventName()).append("'");
        }
        contentBuilder.append(" sắp đến hạn chót.\n");
        contentBuilder.append("- Hạn chót: ").append(task.getDeadline()).append("\n");
        contentBuilder.append("- Thời gian còn lại: ")
                .append(formatRemaining(Duration.between(now, task.getDeadline()))).append("\n");
        if (task.getAdvanceBudget() != null) {
            contentBuilder.append("- Ngân sách tạm ứng dự kiến: ")
                    .append(task.getAdvanceBudget().toPlainString()).append(" VNĐ\n");
        }
        if (task.getAssignedBranchId() != null && !task.getAssignedBranchId().isBlank()) {
            contentBuilder.append("- Nhánh phụ trách: ").append(task.getAssignedBranchId().trim()).append("\n");
        }
        contentBuilder.append("- Cá nhân phụ trách: ").append(task.getAssignedMemberId().trim()).append("\n");
        contentBuilder.append("Vui lòng cập nhật tiến độ hoặc đánh dấu hoàn thành công việc trước khi hết hạn.");
        return contentBuilder.toString();
    }

    private String formatRemaining(Duration remaining) {
        if (remaining.isNegative()) {
            return "0 phút";
        }
        long hours = remaining.toHours();
        long minutes = remaining.toMinutesPart();
        if (hours <= 0) {
            return minutes + " phút";
        }
        return hours + " giờ " + minutes + " phút";
    }
}