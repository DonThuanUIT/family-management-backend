package com.familyfund.event.internal.notification;

import com.familyfund.event.api.dto.TaskAlertLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default implementation of NotificationSender that logs broadcast messages.
 * Acts as an adapter ready to be extended or chained with SMS, Email, or FCM push providers.
 */
@Slf4j
@Component
public class LoggingNotificationSender implements NotificationSender {

    @Override
    public void sendEventBroadcast(String clanId, String eventId, String title, String content) {
        log.info("[ThongBao Broadcast] Phát tin nhắn tới toàn thể thành viên dòng họ [{}] | Sự kiện: [{}] | Tiêu đề: \"{}\" | Nội dung: \"{}\"",
                clanId, eventId, title, content);
    }

    @Override
    public void sendTaskAssignedNotification(String clanId, String eventId, String taskId, String title, String content, List<String> recipientMemberIds) {
        log.info("[ThongBao TaskAssigned] Phân công công việc [{}] sự kiện [{}] dòng họ [{}] cho {} người nhận {} | Tiêu đề: \"{}\" | Nội dung: \"{}\"",
                taskId, eventId, clanId, recipientMemberIds.size(), recipientMemberIds, title, content);
    }

    @Override
    public void sendTaskProgressWarning(String clanId, String eventId, String taskId, TaskAlertLevel alertLevel,
                                        String title, String content, String recipientMemberId, String recipientAccountId) {
        log.info("[ThongBao ProgressWarning] Cảnh báo tiến độ [{}] công việc [{}] sự kiện [{}] dòng họ [{}] tới thành viên [{}] (tài khoản [{}]) | Tiêu đề: \"{}\" | Nội dung: \"{}\"",
                alertLevel, taskId, eventId, clanId, recipientMemberId, recipientAccountId, title, content);
    }

    @Override
    public void sendRoleChangedNotification(String clanId, String targetMemberId, String targetAccountId, String roleCode, String actionType, String title, String content) {
        log.info("[ThongBao RoleChanged] Thông báo phân quyền: clanId=[{}], memberId=[{}], accountId=[{}], role=[{}], action=[{}] | Tiêu đề: \"{}\" | Nội dung: \"{}\"",
                clanId, targetMemberId, targetAccountId, roleCode, actionType, title, content);
    }
}
