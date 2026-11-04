package com.familyfund.event.internal.notification;

import com.familyfund.event.api.event.TaskAssignedDomainEvent;
import com.familyfund.event.internal.entity.EventNotification;
import com.familyfund.event.internal.repository.EventNotificationRepository;
import com.familyfund.event.internal.util.NotificationIdGenerator;
import com.familyfund.genealogy.api.GenealogyQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskNotificationListenerTest {

    private static final String EVENT_ID = "EVT_TEST_001";
    private static final String CLAN_ID = "CLAN_TEST";
    private static final String NOTIF_ID = "NTF_TEST_001";
    private static final String TASK_ID = "TSK_TEST_001";

    @Mock
    private EventNotificationRepository eventNotificationRepository;

    @Mock
    private NotificationSender notificationSender;

    @Mock
    private NotificationIdGenerator notificationIdGenerator;

    @Mock
    private GenealogyQueryService genealogyQueryService;

    @InjectMocks
    private TaskNotificationListener listener;

    @Test
    @DisplayName("Handles TaskAssignedDomainEvent: assigned to individual member only")
    void handleTaskAssigned_IndividualMemberOnly() {
        when(notificationIdGenerator.generate()).thenReturn(NOTIF_ID);

        TaskAssignedDomainEvent event = new TaskAssignedDomainEvent(
                EVENT_ID,
                CLAN_ID,
                "Lễ Thượng Thọ 2026",
                TASK_ID,
                "Chuẩn bị hoa tươi và khánh chúc thọ",
                "MEM_001",
                null,
                new BigDecimal("5000000"),
                Instant.parse("2026-05-01T12:00:00Z"),
                "ACC_ADMIN"
        );

        listener.onTaskAssigned(event);

        verify(genealogyQueryService, never()).getMemberIdsByBranch(any(), any());

        ArgumentCaptor<EventNotification> captor = ArgumentCaptor.forClass(EventNotification.class);
        verify(eventNotificationRepository).save(captor.capture());
        EventNotification saved = captor.getValue();

        assertEquals(NOTIF_ID, saved.getNotificationId());
        assertEquals(EVENT_ID, saved.getEventId());
        assertEquals("ACC_ADMIN", saved.getTriggeredBy());
        assertTrue(saved.getTitle().contains("Chuẩn bị hoa tươi và khánh chúc thọ"));
        assertTrue(saved.getContentChanges().contains("Lễ Thượng Thọ 2026"));
        assertTrue(saved.getContentChanges().contains("5000000 VNĐ"));
        assertTrue(saved.getContentChanges().contains("MEM_001"));
        assertNotNull(saved.getSentTime());

        verify(notificationSender).sendTaskAssignedNotification(
                eq(CLAN_ID),
                eq(EVENT_ID),
                eq(TASK_ID),
                eq(saved.getTitle()),
                eq(saved.getContentChanges()),
                eq(List.of("MEM_001"))
        );
    }

    @Test
    @DisplayName("Handles TaskAssignedDomainEvent: assigned to family branch only, notifies all branch members")
    void handleTaskAssigned_BranchOnly() {
        when(notificationIdGenerator.generate()).thenReturn(NOTIF_ID);
        when(genealogyQueryService.getMemberIdsByBranch(CLAN_ID, "BR_01"))
                .thenReturn(List.of("MEM_001", "MEM_002", "MEM_003"));

        TaskAssignedDomainEvent event = new TaskAssignedDomainEvent(
                EVENT_ID,
                CLAN_ID,
                "Giỗ Tổ 2026",
                TASK_ID,
                "Hậu cần chuẩn bị mâm cỗ",
                null,
                "BR_01",
                new BigDecimal("10000000"),
                Instant.parse("2026-04-20T10:00:00Z"),
                "ACC_BTC"
        );

        listener.onTaskAssigned(event);

        verify(genealogyQueryService).getMemberIdsByBranch(CLAN_ID, "BR_01");

        ArgumentCaptor<EventNotification> captor = ArgumentCaptor.forClass(EventNotification.class);
        verify(eventNotificationRepository).save(captor.capture());
        EventNotification saved = captor.getValue();

        assertEquals("ACC_BTC", saved.getTriggeredBy());
        assertTrue(saved.getContentChanges().contains("BR_01"));

        verify(notificationSender).sendTaskAssignedNotification(
                eq(CLAN_ID),
                eq(EVENT_ID),
                eq(TASK_ID),
                eq(saved.getTitle()),
                eq(saved.getContentChanges()),
                eq(List.of("MEM_001", "MEM_002", "MEM_003"))
        );
    }

    @Test
    @DisplayName("Handles TaskAssignedDomainEvent: assigned to both member and branch, deduplicates recipients")
    void handleTaskAssigned_BothMemberAndBranch_Deduplicates() {
        when(notificationIdGenerator.generate()).thenReturn(NOTIF_ID);
        // Branch contains MEM_001 (same as individual) and MEM_002
        when(genealogyQueryService.getMemberIdsByBranch(CLAN_ID, "BR_01"))
                .thenReturn(List.of("MEM_001", "MEM_002"));

        TaskAssignedDomainEvent event = new TaskAssignedDomainEvent(
                EVENT_ID,
                CLAN_ID,
                "Giỗ Tổ 2026",
                TASK_ID,
                "Thuê rạp và bàn ghế",
                "MEM_001",
                "BR_01",
                new BigDecimal("8000000"),
                Instant.parse("2026-04-25T08:00:00Z"),
                "ACC_LEADER"
        );

        listener.onTaskAssigned(event);

        verify(notificationSender).sendTaskAssignedNotification(
                eq(CLAN_ID),
                eq(EVENT_ID),
                eq(TASK_ID),
                any(),
                any(),
                eq(List.of("MEM_001", "MEM_002"))
        );
    }

    @Test
    @DisplayName("Handles TaskAssignedDomainEvent: truncates long title (>200 chars) and triggeredBy (>20 chars)")
    void handleTaskAssigned_TruncatesLongTitleAndTriggeredBy() {
        when(notificationIdGenerator.generate()).thenReturn(NOTIF_ID);

        String longTaskName = "A".repeat(250);
        String longTriggeredBy = "VERY_LONG_OPERATOR_ACCOUNT_ID_123456789";

        TaskAssignedDomainEvent event = new TaskAssignedDomainEvent(
                EVENT_ID,
                CLAN_ID,
                "Event",
                TASK_ID,
                longTaskName,
                "MEM_001",
                null,
                null,
                null,
                longTriggeredBy
        );

        listener.onTaskAssigned(event);

        ArgumentCaptor<EventNotification> captor = ArgumentCaptor.forClass(EventNotification.class);
        verify(eventNotificationRepository).save(captor.capture());
        EventNotification saved = captor.getValue();

        assertThat(saved.getTitle().length()).isLessThanOrEqualTo(200);
        assertTrue(saved.getTitle().endsWith("..."));
        assertThat(saved.getTriggeredBy().length()).isLessThanOrEqualTo(20);
        assertEquals(longTriggeredBy.substring(0, 20), saved.getTriggeredBy());
    }

    @Test
    @DisplayName("Handles TaskAssignedDomainEvent: defaults triggeredBy to SYSTEM when null or blank")
    void handleTaskAssigned_DefaultsTriggeredByWhenNullOrBlank() {
        when(notificationIdGenerator.generate()).thenReturn(NOTIF_ID);

        TaskAssignedDomainEvent event = new TaskAssignedDomainEvent(
                EVENT_ID,
                CLAN_ID,
                "Event",
                TASK_ID,
                "Task Name",
                "MEM_001",
                null,
                null,
                null,
                "   "
        );

        listener.onTaskAssigned(event);

        ArgumentCaptor<EventNotification> captor = ArgumentCaptor.forClass(EventNotification.class);
        verify(eventNotificationRepository).save(captor.capture());
        assertEquals("SYSTEM", captor.getValue().getTriggeredBy());
    }
}
