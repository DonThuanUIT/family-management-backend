package com.familyfund.event.internal.notification;

import com.familyfund.event.api.event.TaskAssignedDomainEvent;
import com.familyfund.event.internal.entity.EventNotification;
import com.familyfund.event.internal.repository.EventNotificationRepository;
import com.familyfund.event.internal.util.NotificationIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Event listener that asynchronously processes task assignment domain events (US-07).
 * Resolves recipients (individual and/or all living members of the assigned branch),
 * persists the notification record into `event_notifications`, and triggers delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskNotificationListener {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_TRIGGERED_BY_LENGTH = 20;

    private final EventNotificationRepository eventNotificationRepository;
    private final NotificationSender notificationSender;
    private final NotificationIdGenerator notificationIdGenerator;
    private final GenealogyQueryService genealogyQueryService;

    @Async
    @EventListener
    public void onTaskAssigned(TaskAssignedDomainEvent event) {
        log.info("Handling TaskAssignedDomainEvent for task [{}] in event [{}]", event.taskId(), event.eventId());

        // 1. Resolve and deduplicate recipient member IDs
        Set<String> recipientSet = new LinkedHashSet<>();
        if (event.assignedMemberId() != null && !event.assignedMemberId().isBlank()) {
            recipientSet.add(event.assignedMemberId().trim());
        }
        if (event.assignedBranchId() != null && !event.assignedBranchId().isBlank()) {
            List<String> branchMemberIds = genealogyQueryService.getMemberIdsByBranch(event.clanId(), event.assignedBranchId().trim());
            if (branchMemberIds != null) {
                for (String memberId : branchMemberIds) {
                    if (memberId != null && !memberId.isBlank()) {
                        recipientSet.add(memberId.trim());
                    }
                }
            }
        }
        List<String> recipientMemberIds = new ArrayList<>(recipientSet);

        // 2. Build title (enforcing VARCHAR(200) DB constraint)
        String rawTitle = "Phân công công việc: " + (event.taskName() != null ? event.taskName().trim() : event.taskId());
        String title = rawTitle.length() > MAX_TITLE_LENGTH
                ? rawTitle.substring(0, MAX_TITLE_LENGTH - 3) + "..."
                : rawTitle;

        // 3. Build detailed notification content
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("Bạn được phân công công việc '").append(event.taskName()).append("'");
        if (event.eventName() != null && !event.eventName().isBlank()) {
            contentBuilder.append(" trong sự kiện '").append(event.eventName()).append("'");
        }
        contentBuilder.append(".\n");

        if (event.deadline() != null) {
            contentBuilder.append("- Hạn chót: ").append(event.deadline()).append("\n");
        }
        if (event.advanceBudget() != null) {
            contentBuilder.append("- Ngân sách tạm ứng dự kiến: ").append(event.advanceBudget()).append(" VNĐ\n");
        }
        if (event.assignedBranchId() != null && !event.assignedBranchId().isBlank()) {
            contentBuilder.append("- Nhánh phụ trách: ").append(event.assignedBranchId().trim()).append("\n");
        }
        if (event.assignedMemberId() != null && !event.assignedMemberId().isBlank()) {
            contentBuilder.append("- Cá nhân phụ trách: ").append(event.assignedMemberId().trim()).append("\n");
        }
        String content = contentBuilder.toString().trim();

        // 4. Sanitize triggeredBy (enforcing VARCHAR(20) DB constraint)
        String triggeredBy = event.triggeredBy();
        if (triggeredBy == null || triggeredBy.isBlank()) {
            triggeredBy = "SYSTEM";
        } else if (triggeredBy.trim().length() > MAX_TRIGGERED_BY_LENGTH) {
            triggeredBy = triggeredBy.trim().substring(0, MAX_TRIGGERED_BY_LENGTH);
        } else {
            triggeredBy = triggeredBy.trim();
        }

        // 5. Persist EventNotification
        EventNotification notification = EventNotification.builder()
                .notificationId(notificationIdGenerator.generate())
                .eventId(event.eventId())
                .title(title)
                .contentChanges(content)
                .sentTime(Instant.now())
                .triggeredBy(triggeredBy)
                .build();

        eventNotificationRepository.save(notification);

        // 6. Dispatch notification via NotificationSender
        notificationSender.sendTaskAssignedNotification(
                event.clanId(),
                event.eventId(),
                event.taskId(),
                title,
                content,
                recipientMemberIds
        );
    }
}
