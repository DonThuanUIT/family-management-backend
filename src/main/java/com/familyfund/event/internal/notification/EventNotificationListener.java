package com.familyfund.event.internal.notification;

import com.familyfund.event.api.dto.RoleActionType;
import com.familyfund.event.api.dto.RoleScopeType;
import com.familyfund.event.api.event.EventOpenedDomainEvent;
import com.familyfund.event.api.event.EventScheduleChangedDomainEvent;
import com.familyfund.event.api.event.RoleChangedDomainEvent;
import com.familyfund.event.internal.entity.EventNotification;
import com.familyfund.event.internal.repository.EventNotificationRepository;
import com.familyfund.event.internal.util.NotificationIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Event listener that processes domain events asynchronously, persists notifications
 * into the database (table `event_notifications`), and triggers broadcasts via NotificationSender.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventNotificationListener {

    private final EventNotificationRepository eventNotificationRepository;
    private final NotificationSender notificationSender;
    private final NotificationIdGenerator notificationIdGenerator;

    @Async
    @EventListener
    public void onEventOpened(EventOpenedDomainEvent event) {
        log.info("Handling EventOpenedDomainEvent for event: {}", event.eventId());

        String title = "Thông báo mở đăng ký sự kiện: " + event.eventName();
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("Sự kiện '").append(event.eventName()).append("' đã chính thức mở cổng đăng ký tham gia. ")
                .append("Thời gian tổ chức: ").append(event.eventDatetime());
        if (event.eventDateLunar() != null && !event.eventDateLunar().isBlank()) {
            contentBuilder.append(" (Âm lịch: ").append(event.eventDateLunar()).append(")");
        }
        contentBuilder.append(". Địa điểm: ").append(event.location()).append(".");
        String content = contentBuilder.toString();

        EventNotification notification = EventNotification.builder()
                .notificationId(notificationIdGenerator.generate())
                .eventId(event.eventId())
                .title(title)
                .contentChanges(content)
                .sentTime(Instant.now())
                .triggeredBy(event.triggeredBy())
                .build();

        eventNotificationRepository.save(notification);
        notificationSender.sendEventBroadcast(event.clanId(), event.eventId(), title, content);
    }

    @Async
    @EventListener
    public void onEventScheduleChanged(EventScheduleChangedDomainEvent event) {
        log.info("Handling EventScheduleChangedDomainEvent for event: {}", event.eventId());

        EventNotification notification = EventNotification.builder()
                .notificationId(notificationIdGenerator.generate())
                .eventId(event.eventId())
                .title(event.title())
                .contentChanges(event.contentChanges())
                .sentTime(Instant.now())
                .triggeredBy(event.triggeredBy())
                .build();

        eventNotificationRepository.save(notification);
        notificationSender.sendEventBroadcast(event.clanId(), event.eventId(), event.title(), event.contentChanges());
    }

    @Async
    @EventListener
    public void onRoleChanged(RoleChangedDomainEvent event) {
        log.info("Handling RoleChangedDomainEvent: auditId={}, action={}, role={}, targetMember={}",
                event.auditId(), event.actionType(), event.roleCode(), event.targetMemberId());

        String scopeDisplay = event.scopeName() != null ? event.scopeName() : event.scopeId();
        String roleName = event.roleCode() != null ? event.roleCode().getDisplayName() : "Vai trò";

        boolean isAssign = event.actionType() == RoleActionType.ASSIGN;
        String title = isAssign
                ? "Bổ nhiệm vai trò: " + roleName + " (" + scopeDisplay + ")"
                : "Thu hồi vai trò: " + roleName + " (" + scopeDisplay + ")";

        String content = isAssign
                ? "Bạn đã được phân công đảm nhiệm vai trò '" + roleName + "' tại " +
                  (event.scopeType() == RoleScopeType.BRANCH ? "chi nhánh" : "sự kiện") + " '" + scopeDisplay + "'."
                : "Vai trò '" + roleName + "' tại " +
                  (event.scopeType() == RoleScopeType.BRANCH ? "chi nhánh" : "sự kiện") + " '" + scopeDisplay + "' đã được thu hồi.";

        if (event.description() != null && !event.description().isBlank()) {
            content += " Ghi chú: " + event.description();
        }

        // 1. Dispatch role changed notification via NotificationSender
        notificationSender.sendRoleChangedNotification(
                event.clanId(),
                event.targetMemberId(),
                event.targetAccountId(),
                event.roleCode() != null ? event.roleCode().getCode() : null,
                event.actionType() != null ? event.actionType().name() : null,
                title,
                content
        );

        // 2. If this is an event scope role, also record in event_notifications
        if (event.scopeType() == RoleScopeType.EVENT && event.scopeId() != null) {
            EventNotification notification = EventNotification.builder()
                    .notificationId(notificationIdGenerator.generate())
                    .eventId(event.scopeId())
                    .title(title)
                    .contentChanges(content)
                    .sentTime(Instant.now())
                    .triggeredBy(event.actorAccountId())
                    .build();
            eventNotificationRepository.save(notification);
        }
    }
}
