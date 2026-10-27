package com.familyfund.event.internal.notification;

import com.familyfund.event.api.event.EventOpenedDomainEvent;
import com.familyfund.event.api.event.EventScheduleChangedDomainEvent;
import com.familyfund.event.internal.entity.EventNotification;
import com.familyfund.event.internal.repository.EventNotificationRepository;
import com.familyfund.event.internal.util.NotificationIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventNotificationListenerTest {

    private static final String EVENT_ID = "EVT_TEST_001";
    private static final String CLAN_ID = "CLAN_TEST";
    private static final String NOTIF_ID = "NTF_TEST_001";

    @Mock
    private EventNotificationRepository eventNotificationRepository;

    @Mock
    private NotificationSender notificationSender;

    @Mock
    private NotificationIdGenerator notificationIdGenerator;

    @InjectMocks
    private EventNotificationListener listener;

    @Test
    @DisplayName("Handles EventOpenedDomainEvent: saves notification and broadcasts")
    void handleEventOpened() {
        when(notificationIdGenerator.generate()).thenReturn(NOTIF_ID);

        EventOpenedDomainEvent event = new EventOpenedDomainEvent(
                EVENT_ID,
                CLAN_ID,
                "Giỗ Tổ 2026",
                Instant.parse("2026-04-26T08:00:00Z"),
                "10/03 Bính Ngọ",
                "Từ đường Họ Nguyễn",
                "ACC_ADMIN"
        );

        listener.onEventOpened(event);

        ArgumentCaptor<EventNotification> captor = ArgumentCaptor.forClass(EventNotification.class);
        verify(eventNotificationRepository).save(captor.capture());
        EventNotification saved = captor.getValue();

        assertEquals(NOTIF_ID, saved.getNotificationId());
        assertEquals(EVENT_ID, saved.getEventId());
        assertEquals("ACC_ADMIN", saved.getTriggeredBy());
        assertTrue(saved.getTitle().contains("Giỗ Tổ 2026"));
        assertTrue(saved.getContentChanges().contains("Từ đường Họ Nguyễn"));
        assertTrue(saved.getContentChanges().contains("10/03 Bính Ngọ"));
        assertNotNull(saved.getSentTime());

        verify(notificationSender).sendEventBroadcast(
                eq(CLAN_ID),
                eq(EVENT_ID),
                eq(saved.getTitle()),
                eq(saved.getContentChanges())
        );
    }

    @Test
    @DisplayName("Handles EventScheduleChangedDomainEvent: saves notification and broadcasts")
    void handleScheduleChanged() {
        when(notificationIdGenerator.generate()).thenReturn(NOTIF_ID);

        EventScheduleChangedDomainEvent event = new EventScheduleChangedDomainEvent(
                EVENT_ID,
                CLAN_ID,
                "Giỗ Tổ 2026",
                "Thông báo thay đổi lịch trình sự kiện: Giỗ Tổ 2026",
                "Thời gian: từ 'A' sang 'B'; Địa điểm: từ 'C' sang 'D'",
                "ACC_HEAD"
        );

        listener.onEventScheduleChanged(event);

        ArgumentCaptor<EventNotification> captor = ArgumentCaptor.forClass(EventNotification.class);
        verify(eventNotificationRepository).save(captor.capture());
        EventNotification saved = captor.getValue();

        assertEquals(NOTIF_ID, saved.getNotificationId());
        assertEquals(EVENT_ID, saved.getEventId());
        assertEquals("ACC_HEAD", saved.getTriggeredBy());
        assertEquals("Thông báo thay đổi lịch trình sự kiện: Giỗ Tổ 2026", saved.getTitle());
        assertEquals("Thời gian: từ 'A' sang 'B'; Địa điểm: từ 'C' sang 'D'", saved.getContentChanges());

        verify(notificationSender).sendEventBroadcast(
                eq(CLAN_ID),
                eq(EVENT_ID),
                eq(event.title()),
                eq(event.contentChanges())
        );
    }
}
