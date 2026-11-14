package com.familyfund.notification.internal.controller;

import com.familyfund.core.exception.GlobalExceptionHandler;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationService;
import com.familyfund.notification.api.dto.NotificationResponse;
import com.familyfund.notification.api.dto.UnreadCountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests of the inbox endpoints ({@link NotificationController}, US-39): request/response contract,
 * pagination defaults, category filters and Bean Validation.
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final String PATH = "/api/v1/notifications";
    private static final String NOTIFICATION_ID = "NTC_TEST_0001";

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private NotificationResponse response(boolean read) {
        return NotificationResponse.builder()
                .notificationId(NOTIFICATION_ID)
                .clanId("CLAN_01")
                .category(NotificationCategory.Task)
                .title("Bạn được giao việc")
                .content("Nội dung")
                .referenceType("TASK")
                .referenceId("TSK_0001")
                .isRead(read)
                .readAt(read ? Instant.parse("2026-04-20T09:00:00Z") : null)
                .createdAt(Instant.parse("2026-04-19T09:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/notifications - 200 with the caller's page and default pagination")
    void list_UsesDefaults() throws Exception {
        when(notificationService.findMyNotifications(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response(false)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$.data.content[0].category").value("Task"))
                .andExpect(jsonPath("$.data.content[0].isRead").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).findMyNotifications(isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications?category=Finance&isRead=false - 200 passes both filters")
    void list_PassesFilters() throws Exception {
        when(notificationService.findMyNotifications(eq(NotificationCategory.Finance), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(PATH)
                        .param("category", "Finance")
                        .param("isRead", "false")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).findMyNotifications(eq(NotificationCategory.Finance), eq(false), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("GET /api/v1/notifications?size=500 - caps the page size at 100")
    void list_CapsPageSize() throws Exception {
        when(notificationService.findMyNotifications(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(PATH).param("size", "500")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).findMyNotifications(isNull(), isNull(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("GET /api/v1/notifications?category=Bogus - 400 for an unknown category")
    void list_RejectsUnknownCategory() throws Exception {
        mockMvc.perform(get(PATH).param("category", "Bogus"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count - 200 with total and per-category counters")
    void unreadCount_ReturnsCounters() throws Exception {
        when(notificationService.unreadSummary())
                .thenReturn(new UnreadCountResponse(4, new UnreadCountResponse.CategoryCount(1, 2, 1)));

        mockMvc.perform(get(PATH + "/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.byCategory.event").value(1))
                .andExpect(jsonPath("$.data.byCategory.task").value(2))
                .andExpect(jsonPath("$.data.byCategory.finance").value(1));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read - 200 with is_read = true")
    void markAsRead_ReturnsUpdatedNotification() throws Exception {
        when(notificationService.markAsRead(NOTIFICATION_ID)).thenReturn(response(true));

        mockMvc.perform(patch(PATH + "/{id}/read", NOTIFICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read - 404 for an unknown notification")
    void markAsRead_NotFound() throws Exception {
        when(notificationService.markAsRead("NTC_MISSING"))
                .thenThrow(new ResourceNotFoundException("Thông báo", "notificationId", "NTC_MISSING"));

        mockMvc.perform(patch(PATH + "/{id}/read", "NTC_MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/read-all - 200 with the number of rows updated")
    void markAllAsRead_ReturnsCount() throws Exception {
        when(notificationService.markAllAsRead(null)).thenReturn(9);

        mockMvc.perform(patch(PATH + "/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markedCount").value(9));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/read-all?category=Task - 200 passes the category through")
    void markAllAsRead_ByCategory() throws Exception {
        when(notificationService.markAllAsRead(NotificationCategory.Task)).thenReturn(3);

        mockMvc.perform(patch(PATH + "/read-all").param("category", "Task"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markedCount").value(3));

        verify(notificationService).markAllAsRead(NotificationCategory.Task);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/device-tokens - 201 with the registered token id")
    void registerDeviceToken_Returns201() throws Exception {
        when(notificationService.registerDeviceToken("fcm-token-abc", "Android")).thenReturn("DVT_TEST_0001");

        mockMvc.perform(post(PATH + "/device-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"fcm-token-abc\",\"platform\":\"Android\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenId").value("DVT_TEST_0001"))
                .andExpect(jsonPath("$.data.platform").value("Android"));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/device-tokens - 400 when fcmToken is blank")
    void registerDeviceToken_RejectsBlankToken() throws Exception {
        mockMvc.perform(post(PATH + "/device-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"   \",\"platform\":\"Android\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/notifications/device-tokens - 400 for an unsupported platform")
    void registerDeviceToken_RejectsUnknownPlatform() throws Exception {
        mockMvc.perform(post(PATH + "/device-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"fcm-token-abc\",\"platform\":\"Blackberry\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/notifications/device-tokens?fcmToken=... - 200 deactivates the token")
    void unregisterDeviceToken_Returns200() throws Exception {
        mockMvc.perform(delete(PATH + "/device-tokens").param("fcmToken", "fcm-token-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).unregisterDeviceToken("fcm-token-abc");
    }
}
