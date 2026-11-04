package com.familyfund.identity.internal.notification;

import com.familyfund.identity.api.event.JoinRequestReviewedDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link JoinRequestReviewedDomainEvent} and notifies the applicant of the outcome (US-17).
 *
 * <p>Asynchronous and detached from the review transaction: a failing notification must never roll back an
 * approval or a rejection, which is why the listener only reads the event payload and logs failures.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JoinRequestNotificationListener {

    private final JoinRequestNotificationSender notificationSender;

    @Async
    @EventListener
    public void onJoinRequestReviewed(JoinRequestReviewedDomainEvent event) {
        if (event.phoneNumber() == null || event.phoneNumber().isBlank()) {
            log.warn("Skipping join-request notification for request [{}]: applicant has no phone number",
                    event.requestId());
            return;
        }

        String title = event.approved()
                ? "Đơn gia nhập dòng họ đã được phê duyệt"
                : "Đơn gia nhập dòng họ bị từ chối";
        String content = event.approved() ? approvedContent(event) : rejectedContent(event);

        try {
            notificationSender.sendReviewResult(event, title, content);
        } catch (RuntimeException ex) {
            // Delivery is best-effort: the review decision is already committed.
            log.error("Failed to notify applicant of join request [{}]: {}", event.requestId(), ex.getMessage());
        }
    }

    private String approvedContent(JoinRequestReviewedDomainEvent event) {
        StringBuilder content = new StringBuilder();
        content.append("Xin chào ").append(displayName(event)).append(", đơn gia nhập dòng họ của bạn đã được phê duyệt");
        if (event.targetBranchName() != null && !event.targetBranchName().isBlank()) {
            content.append(" vào nhánh '").append(event.targetBranchName()).append("'");
        }
        content.append(".\n");
        if (event.memberId() != null && !event.memberId().isBlank()) {
            content.append("- Mã hồ sơ phả hệ: ").append(event.memberId()).append("\n");
        }
        content.append("- Bạn có thể đăng nhập bằng số điện thoại ").append(event.phoneNumber()).append(".");
        return content.toString();
    }

    private String rejectedContent(JoinRequestReviewedDomainEvent event) {
        StringBuilder content = new StringBuilder();
        content.append("Xin chào ").append(displayName(event)).append(", đơn gia nhập dòng họ của bạn đã bị từ chối.\n");
        if (event.rejectionReason() != null && !event.rejectionReason().isBlank()) {
            content.append("- Lý do: ").append(event.rejectionReason()).append("\n");
        }
        content.append("- Nếu cho rằng có sai sót, vui lòng liên hệ Trưởng tộc/Trưởng chi để được hỗ trợ.");
        return content.toString();
    }

    private String displayName(JoinRequestReviewedDomainEvent event) {
        return event.fullName() != null && !event.fullName().isBlank() ? event.fullName() : event.phoneNumber();
    }
}