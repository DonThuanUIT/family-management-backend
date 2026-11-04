package com.familyfund.identity.internal.notification;

import com.familyfund.identity.api.event.JoinRequestReviewedDomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JoinRequestNotificationListenerTest {

    private static final Instant REVIEWED_AT = Instant.parse("2026-11-04T10:00:00Z");

    @Mock
    private JoinRequestNotificationSender notificationSender;

    @InjectMocks
    private JoinRequestNotificationListener listener;

    private JoinRequestReviewedDomainEvent approvedEvent() {
        return new JoinRequestReviewedDomainEvent(
                "REQ_001", "ACC_001", "0987654321", "Nguyễn Văn An",
                "CLAN_01", "BR_CHI_2", "Chi 2", true, null,
                "MEM_0001", "ACC_ADMIN", REVIEWED_AT);
    }

    private JoinRequestReviewedDomainEvent rejectedEvent() {
        return new JoinRequestReviewedDomainEvent(
                "REQ_002", "ACC_002", "0987654322", "Trần Thị Bình",
                "CLAN_01", "BR_ROOT", "Chi Gốc", false, "Thông tin không khớp",
                null, "ACC_ADMIN", REVIEWED_AT);
    }

    @Test
    @DisplayName("Approved request notifies the applicant with the branch and member id")
    void onJoinRequestReviewed_Approved_SendsApprovalMessage() {
        listener.onJoinRequestReviewed(approvedEvent());

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).sendReviewResult(any(JoinRequestReviewedDomainEvent.class),
                titleCaptor.capture(), contentCaptor.capture());

        assertEquals("Đơn gia nhập dòng họ đã được phê duyệt", titleCaptor.getValue());
        assertTrue(contentCaptor.getValue().contains("Chi 2"));
        assertTrue(contentCaptor.getValue().contains("MEM_0001"));
        assertTrue(contentCaptor.getValue().contains("0987654321"));
    }

    @Test
    @DisplayName("Rejected request notifies the applicant with the rejection reason")
    void onJoinRequestReviewed_Rejected_SendsRejectionReason() {
        listener.onJoinRequestReviewed(rejectedEvent());

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender).sendReviewResult(any(JoinRequestReviewedDomainEvent.class),
                titleCaptor.capture(), contentCaptor.capture());

        assertEquals("Đơn gia nhập dòng họ bị từ chối", titleCaptor.getValue());
        assertTrue(contentCaptor.getValue().contains("Thông tin không khớp"));
    }

    @Test
    @DisplayName("An applicant without a phone number is skipped instead of failing the review")
    void onJoinRequestReviewed_NoPhoneNumber_Skips() {
        JoinRequestReviewedDomainEvent event = new JoinRequestReviewedDomainEvent(
                "REQ_003", "ACC_003", null, "Vô Danh",
                "CLAN_01", "BR_ROOT", "Chi Gốc", true, null,
                "MEM_0003", "ACC_ADMIN", REVIEWED_AT);

        listener.onJoinRequestReviewed(event);

        verify(notificationSender, never()).sendReviewResult(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("A failing delivery is swallowed so the committed review is not affected")
    void onJoinRequestReviewed_SenderFails_DoesNotPropagate() {
        doThrow(new IllegalStateException("SMS gateway down"))
                .when(notificationSender).sendReviewResult(any(), anyString(), anyString());

        listener.onJoinRequestReviewed(approvedEvent());

        verify(notificationSender).sendReviewResult(any(), anyString(), anyString());
    }
}