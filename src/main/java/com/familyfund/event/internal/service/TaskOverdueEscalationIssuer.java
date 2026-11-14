package com.familyfund.event.internal.service;

import com.familyfund.event.api.dto.EventStatus;
import com.familyfund.event.api.dto.TaskAlertLevel;
import com.familyfund.event.api.dto.TaskStatus;
import com.familyfund.event.internal.entity.Event;
import com.familyfund.event.internal.entity.EventTask;
import com.familyfund.event.internal.entity.TaskProgressAlert;
import com.familyfund.event.internal.notification.NotificationSender;
import com.familyfund.event.internal.repository.EventRepository;
import com.familyfund.event.internal.repository.EventTaskRepository;
import com.familyfund.event.internal.repository.TaskProgressAlertRepository;
import com.familyfund.event.internal.util.AlertIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import com.familyfund.genealogy.api.dto.BranchSummaryDto;
import com.familyfund.genealogy.api.dto.MemberSummaryDto;
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
 * Issues a single {@code OverdueAlert12H} escalation for one unfinished event task that went past its
 * deadline by at least 12 hours (US-47 - leo thang quá hạn 12h).
 *
 * <p>Unlike the 24h {@link TaskProgressWarningIssuer}, which nudges the assignee, this alert is
 * addressed to the <b>manager</b>: the recipient is the representative (Trưởng chi) of the branch the
 * task belongs to ({@code family_branches.representative_id}), while the content names the person in
 * charge ({@code event_tasks.assigned_member_id}) and how many hours the task is late. The branch is
 * {@code event_tasks.assigned_branch_id} when the task was assigned to a branch, otherwise the home
 * branch of the assigned member.</p>
 *
 * <p>One row per task, exactly like the 24h pass: {@code (task_id, OverdueAlert12H)} in
 * {@code task_progress_alerts} is the idempotency key, so a task that stays overdue for days is
 * escalated once. The alert row is written <b>before</b> the notification is dispatched, so a failing
 * sender rolls the transaction back and the task is retried on the next scan pass.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskOverdueEscalationIssuer {

    /** A task is escalated to its branch representative once it is at least this late. */
    public static final Duration ESCALATION_DELAY = Duration.ofHours(12);

    private static final int MAX_TITLE_LENGTH = 200;

    private final EventTaskRepository eventTaskRepository;
    private final EventRepository eventRepository;
    private final TaskProgressAlertRepository taskProgressAlertRepository;
    private final AlertIdGenerator alertIdGenerator;
    private final GenealogyQueryService genealogyQueryService;
    private final IdentityRoleCommandService identityRoleCommandService;
    private final NotificationSender notificationSender;

    /**
     * Evaluates one overdue task and, when all escalation conditions hold, persists the
     * {@code OverdueAlert12H} log and notifies the branch representative.
     *
     * @param taskId ID of the candidate task
     * @param now    reference instant shared by the whole scan pass
     * @return {@code true} when a new escalation was issued, {@code false} when skipped
     */
    @Transactional
    public boolean issueOverdueEscalation(String taskId, Instant now) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        Instant referenceInstant = (now != null) ? now : Instant.now();

        EventTask task = eventTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.debug("US-47 escalation: task [{}] no longer exists, skipping", taskId);
            return false;
        }

        if (!isEscalationCandidate(task, referenceInstant)) {
            return false;
        }

        // Idempotency: one OverdueAlert12H escalation per task.
        if (taskProgressAlertRepository.existsByTaskIdAndAlertLevel(task.getTaskId(), TaskAlertLevel.OverdueAlert12H)) {
            log.debug("US-47 escalation: task [{}] already escalated, skipping", task.getTaskId());
            return false;
        }

        Event event = eventRepository.findById(task.getEventId()).orElse(null);
        if (event == null || event.getStatus() == EventStatus.Closed) {
            log.debug("US-47 escalation: event [{}] of task [{}] is closed or missing, skipping",
                    task.getEventId(), task.getTaskId());
            return false;
        }

        // 1. Branch responsible for the task: the assigned branch, or the assignee's home branch.
        String branchId = resolveResponsibleBranchId(task);
        if (branchId == null) {
            log.debug("US-47 escalation: task [{}] has no responsible branch, skipping", task.getTaskId());
            return false;
        }

        BranchSummaryDto branch = genealogyQueryService
                .findBranchByIdAndClanId(branchId, event.getClanId())
                .orElse(null);
        if (branch == null) {
            log.warn("US-47 escalation: branch [{}] of task [{}] not found in clan [{}], cannot escalate",
                    branchId, task.getTaskId(), event.getClanId());
            return false;
        }

        // 2. Recipient = representative of that branch (the manager who has to push the task).
        String representativeMemberId = trimToNull(branch.representativeMemberId());
        if (representativeMemberId == null) {
            log.warn("US-47 escalation: branch [{}] of task [{}] has no representative, cannot escalate",
                    branchId, task.getTaskId());
            return false;
        }

        Optional<AccountSummaryDto> representativeAccount =
                identityRoleCommandService.findAccountByMemberId(representativeMemberId);
        if (representativeAccount.isEmpty()) {
            // `task_progress_alerts.recipient_account_id` is NOT NULL: no account means no channel.
            log.warn("US-47 escalation: representative [{}] of branch [{}] has no linked user account, cannot escalate",
                    representativeMemberId, branchId);
            return false;
        }

        // 3. Person in charge, resolved only for the message body.
        String assigneeMemberId = trimToNull(task.getAssignedMemberId());
        String assigneeName = resolveMemberName(assigneeMemberId);

        Duration overdue = Duration.between(task.getDeadline(), referenceInstant);
        String title = buildTitle(task);
        String content = buildContent(task, event, branch, assigneeMemberId, assigneeName, representativeMemberId, overdue);

        TaskProgressAlert alert = TaskProgressAlert.builder()
                .alertId(alertIdGenerator.generate())
                .taskId(task.getTaskId())
                .alertLevel(TaskAlertLevel.OverdueAlert12H)
                .recipientAccountId(representativeAccount.get().accountId())
                .alertContent(content)
                .sentTime(referenceInstant)
                .isRead(Boolean.FALSE)
                .build();

        taskProgressAlertRepository.save(alert);

        notificationSender.sendTaskProgressWarning(
                event.getClanId(),
                event.getEventId(),
                task.getTaskId(),
                TaskAlertLevel.OverdueAlert12H,
                title,
                content,
                representativeMemberId,
                representativeAccount.get().accountId()
        );

        log.info("US-47 escalation: alert [{}] issued for overdue task [{}] ({} late) to branch [{}] representative [{}]",
                alert.getAlertId(), task.getTaskId(), formatOverdue(overdue), branchId, representativeMemberId);
        return true;
    }

    private boolean isEscalationCandidate(EventTask task, Instant now) {
        if (task.getStatus() == TaskStatus.Completed) {
            log.debug("US-47 escalation: task [{}] is completed, skipping", task.getTaskId());
            return false;
        }
        Instant deadline = task.getDeadline();
        if (deadline == null) {
            log.debug("US-47 escalation: task [{}] has no deadline, skipping", task.getTaskId());
            return false;
        }
        if (deadline.isAfter(now.minus(ESCALATION_DELAY))) {
            log.debug("US-47 escalation: task [{}] deadline [{}] is not overdue by 12h yet, skipping",
                    task.getTaskId(), deadline);
            return false;
        }
        if (trimToNull(task.getAssignedBranchId()) == null && trimToNull(task.getAssignedMemberId()) == null) {
            log.debug("US-47 escalation: task [{}] has no assigned branch or member, nothing to escalate",
                    task.getTaskId());
            return false;
        }
        return true;
    }

    /**
     * The branch a late task is escalated through: {@code assigned_branch_id} when the task was given to a
     * branch, otherwise the branch the assigned member belongs to.
     */
    private String resolveResponsibleBranchId(EventTask task) {
        String assignedBranchId = trimToNull(task.getAssignedBranchId());
        if (assignedBranchId != null) {
            return assignedBranchId;
        }
        String assigneeMemberId = trimToNull(task.getAssignedMemberId());
        if (assigneeMemberId == null) {
            return null;
        }
        return genealogyQueryService.findMemberSummary(assigneeMemberId)
                .map(MemberSummaryDto::branchId)
                .map(value -> trimToNull(value))
                .orElse(null);
    }

    private String resolveMemberName(String memberId) {
        if (memberId == null) {
            return null;
        }
        return genealogyQueryService.findMemberSummary(memberId)
                .map(MemberSummaryDto::fullName)
                .map(value -> trimToNull(value))
                .orElse(null);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildTitle(EventTask task) {
        String taskName = (task.getTaskName() != null && !task.getTaskName().isBlank())
                ? task.getTaskName().trim()
                : task.getTaskId();
        String rawTitle = "Cảnh báo quá hạn 12h: " + taskName;
        return rawTitle.length() > MAX_TITLE_LENGTH
                ? rawTitle.substring(0, MAX_TITLE_LENGTH - 3) + "..."
                : rawTitle;
    }

    private String buildContent(EventTask task,
                                Event event,
                                BranchSummaryDto branch,
                                String assigneeMemberId,
                                String assigneeName,
                                String representativeMemberId,
                                Duration overdue) {
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("Công việc '").append(task.getTaskName()).append("'");
        if (event.getEventName() != null && !event.getEventName().isBlank()) {
            contentBuilder.append(" của sự kiện '").append(event.getEventName()).append("'");
        }
        contentBuilder.append(" đã quá hạn chót ").append(formatOverdue(overdue)).append(".\n");
        contentBuilder.append("- Hạn chót: ").append(task.getDeadline()).append("\n");
        contentBuilder.append("- Thời gian quá hạn: ").append(formatOverdue(overdue)).append("\n");
        if (assigneeMemberId == null) {
            contentBuilder.append("- Người phụ trách: Chưa phân công cá nhân\n");
        } else if (assigneeName != null) {
            contentBuilder.append("- Người phụ trách: ").append(assigneeName)
                    .append(" (").append(assigneeMemberId).append(")\n");
        } else {
            contentBuilder.append("- Người phụ trách: ").append(assigneeMemberId).append("\n");
        }
        contentBuilder.append("- Nhánh phụ trách: ").append(branch.branchId());
        if (branch.branchName() != null && !branch.branchName().isBlank()) {
            contentBuilder.append(" - ").append(branch.branchName());
        }
        contentBuilder.append("\n");
        contentBuilder.append("- Trưởng chi nhận cảnh báo: ");
        if (branch.representativeFullName() != null && !branch.representativeFullName().isBlank()) {
            contentBuilder.append(branch.representativeFullName()).append(" ");
        }
        contentBuilder.append("(").append(representativeMemberId).append(")\n");
        contentBuilder.append("Đề nghị Trưởng chi kiểm tra tiến độ và đôn đốc hoàn thành công việc.");
        return contentBuilder.toString();
    }

    private String formatOverdue(Duration overdue) {
        if (overdue.isNegative()) {
            return "0 phút";
        }
        long hours = overdue.toHours();
        long minutes = overdue.toMinutesPart();
        if (hours <= 0) {
            return minutes + " phút";
        }
        return hours + " giờ " + minutes + " phút";
    }
}
