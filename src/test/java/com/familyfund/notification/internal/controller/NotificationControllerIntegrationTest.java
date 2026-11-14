package com.familyfund.notification.internal.controller;

import com.familyfund.core.security.MemberLinkedPrincipal;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.core.tenant.TenantFilter;
import com.familyfund.genealogy.internal.entity.Clan;
import com.familyfund.genealogy.internal.repository.ClanRepository;
import com.familyfund.identity.internal.entity.AccountStatus;
import com.familyfund.identity.internal.entity.UserAccount;
import com.familyfund.identity.internal.repository.UserAccountRepository;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.NotificationService;
import com.familyfund.notification.internal.entity.NotificationDeviceToken;
import com.familyfund.notification.internal.repository.NotificationDeviceTokenRepository;
import com.familyfund.notification.internal.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the notification inbox (US-39) over the real stack: Spring Security, {@code TenantFilter},
 * {@code NotificationService}, JPA and the Flyway schema (V23) in PostgreSQL.
 *
 * <p>Every test runs inside a transaction that is rolled back, so the shared database keeps no notification rows.
 * Notifications are created through {@link NotificationService} — the same entry point the event/contribution
 * modules use — and then read back over HTTP as the recipient.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerIntegrationTest {

    private static final String CLAN = "CLAN_NTF_IT";
    private static final String OTHER_CLAN = "CLAN_NTF_IT_OTHER";
    private static final String PATH = "/api/v1/notifications";
    private static final String ACCOUNT = "ACC_NTF_IT_1";
    private static final String OTHER_ACCOUNT = "ACC_NTF_IT_2";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClanRepository clanRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationDeviceTokenRepository deviceTokenRepository;
    @Autowired
    private NotificationService notificationService;

    private record Principal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @BeforeEach
    void seed() {
        TenantContext.setClanId(CLAN);
        clanRepository.save(Clan.builder().clanId(CLAN).clanName("NTF IT Clan").build());
        clanRepository.save(Clan.builder().clanId(OTHER_CLAN).clanName("NTF IT Other").build());

        userAccountRepository.save(UserAccount.builder()
                .accountId(ACCOUNT).phoneNumber("0911200001").passwordHash("{noop}test")
                .accountStatus(AccountStatus.Active).build());
        userAccountRepository.save(UserAccount.builder()
                .accountId(OTHER_ACCOUNT).phoneNumber("0911200002").passwordHash("{noop}test")
                .accountStatus(AccountStatus.Active).build());
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ helpers

    private Authentication auth(String accountId) {
        return new UsernamePasswordAuthenticationToken(
                new Principal(accountId, null), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
    }

    private String createNotification(String accountId, NotificationCategory category, String title) {
        return notificationService.notify(NotificationCommand.builder()
                .clanId(CLAN)
                .recipientAccountId(accountId)
                .category(category)
                .title(title)
                .content("Nội dung: " + title)
                .referenceType("TASK")
                .referenceId("TSK_IT_01")
                .build());
    }

    /**
     * Marks a row as read straight through the repository: {@code NotificationService#markAsRead} is self-scoped to
     * the authenticated caller, and this seeding happens outside any HTTP request.
     */
    private void markReadDirectly(String notificationId) {
        var stored = notificationRepository.findById(notificationId).orElseThrow();
        stored.setIsRead(Boolean.TRUE);
        stored.setReadAt(java.time.Instant.now());
        notificationRepository.save(stored);
    }

    // ------------------------------------------------------------------ listing

    @Test
    @DisplayName("GET /api/v1/notifications - 200 only returns the caller's own notifications")
    void listIsScopedToCaller() throws Exception {
        createNotification(ACCOUNT, NotificationCategory.Task, "Của tôi");
        createNotification(OTHER_ACCOUNT, NotificationCategory.Task, "Của người khác");

        mockMvc.perform(get(PATH)
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Của tôi"))
                .andExpect(jsonPath("$.data.content[0].category").value("Task"))
                .andExpect(jsonPath("$.data.content[0].isRead").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/notifications?category=Finance - 200 filters by category")
    void listFiltersByCategory() throws Exception {
        createNotification(ACCOUNT, NotificationCategory.Task, "Việc");
        createNotification(ACCOUNT, NotificationCategory.Finance, "Tiền");

        mockMvc.perform(get(PATH)
                        .param("category", "Finance")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Tiền"))
                .andExpect(jsonPath("$.data.content[0].category").value("Finance"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications?isRead=false - 200 hides read notifications")
    void listFiltersUnread() throws Exception {
        String unreadId = createNotification(ACCOUNT, NotificationCategory.Event, "Chưa đọc");
        String readId = createNotification(ACCOUNT, NotificationCategory.Event, "Đã đọc");
        markReadDirectly(readId);

        mockMvc.perform(get(PATH)
                        .param("isRead", "false")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(unreadId));
    }

    @Test
    @DisplayName("GET /api/v1/notifications - 401 for an anonymous caller")
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH).header(TenantFilter.HEADER_CLAN_ID, CLAN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count - 200 with the per-category badge")
    void unreadCountReflectsStoredRows() throws Exception {
        createNotification(ACCOUNT, NotificationCategory.Task, "Việc 1");
        createNotification(ACCOUNT, NotificationCategory.Task, "Việc 2");
        createNotification(ACCOUNT, NotificationCategory.Finance, "Tiền");
        String readId = createNotification(ACCOUNT, NotificationCategory.Event, "Sự kiện");
        markReadDirectly(readId);

        mockMvc.perform(get(PATH + "/unread-count")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.byCategory.event").value(0))
                .andExpect(jsonPath("$.data.byCategory.task").value(2))
                .andExpect(jsonPath("$.data.byCategory.finance").value(1));
    }

    // ------------------------------------------------------------------ mark as read

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read - 200 persists is_read and read_at")
    void markAsReadPersistsState() throws Exception {
        String id = createNotification(ACCOUNT, NotificationCategory.Task, "Việc");

        mockMvc.perform(patch(PATH + "/{id}/read", id)
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(id))
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        var stored = notificationRepository.findById(id).orElseThrow();
        assertThat(stored.getIsRead()).isTrue();
        assertThat(stored.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read - 404 when the notification belongs to another account")
    void markAsReadForeignNotificationIsNotFound() throws Exception {
        String foreignId = createNotification(OTHER_ACCOUNT, NotificationCategory.Task, "Của người khác");

        mockMvc.perform(patch(PATH + "/{id}/read", foreignId)
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(notificationRepository.findById(foreignId).orElseThrow().getIsRead()).isFalse();
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/read-all - 200 marks the caller's rows only")
    void markAllAsReadIsScopedToCaller() throws Exception {
        createNotification(ACCOUNT, NotificationCategory.Task, "Việc 1");
        createNotification(ACCOUNT, NotificationCategory.Finance, "Tiền");
        String foreignId = createNotification(OTHER_ACCOUNT, NotificationCategory.Task, "Của người khác");

        mockMvc.perform(patch(PATH + "/read-all")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markedCount").value(2));

        assertThat(notificationRepository.findById(foreignId).orElseThrow().getIsRead()).isFalse();
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/read-all?category=Finance - 200 marks one category only")
    void markAllAsReadByCategory() throws Exception {
        String taskId = createNotification(ACCOUNT, NotificationCategory.Task, "Việc");
        createNotification(ACCOUNT, NotificationCategory.Finance, "Tiền");

        mockMvc.perform(patch(PATH + "/read-all")
                        .param("category", "Finance")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markedCount").value(1));

        assertThat(notificationRepository.findById(taskId).orElseThrow().getIsRead()).isFalse();
    }

    // ------------------------------------------------------------------ device tokens

    @Test
    @DisplayName("POST /api/v1/notifications/device-tokens - 201 stores the caller's FCM token")
    void registerDeviceTokenPersistsRow() throws Exception {
        mockMvc.perform(post(PATH + "/device-tokens")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"fcm-it-token-1\",\"platform\":\"Android\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tokenId").isNotEmpty())
                .andExpect(jsonPath("$.data.platform").value("Android"));

        NotificationDeviceToken stored = deviceTokenRepository.findByFcmToken("fcm-it-token-1").orElseThrow();
        assertThat(stored.getAccountId()).isEqualTo(ACCOUNT);
        assertThat(stored.getIsActive()).isTrue();
        assertThat(stored.getLastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("POST /api/v1/notifications/device-tokens - 400 when the platform is unknown")
    void registerDeviceTokenRejectsUnknownPlatform() throws Exception {
        mockMvc.perform(post(PATH + "/device-tokens")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"fcm-it-token-2\",\"platform\":\"Symbian\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(deviceTokenRepository.findByFcmToken("fcm-it-token-2")).isEmpty();
    }

    @Test
    @DisplayName("DELETE /api/v1/notifications/device-tokens - 200 deactivates the token")
    void unregisterDeviceTokenDeactivates() throws Exception {
        mockMvc.perform(post(PATH + "/device-tokens")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fcmToken\":\"fcm-it-token-3\",\"platform\":\"Web\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(PATH + "/device-tokens")
                        .param("fcmToken", "fcm-it-token-3")
                        .header(TenantFilter.HEADER_CLAN_ID, CLAN)
                        .with(authentication(auth(ACCOUNT))))
                .andExpect(status().isOk());

        assertThat(deviceTokenRepository.findByFcmToken("fcm-it-token-3").orElseThrow().getIsActive()).isFalse();
    }

    // ------------------------------------------------------------------ push payload

    @Test
    @DisplayName("a notification written for a logged-out device is still persisted in the inbox")
    void notificationSurvivesDisabledPush() {
        createNotification(ACCOUNT, NotificationCategory.Finance, "Biên nhận");

        // The test profile uses the logging push adapter (app.notification.push.provider=log), so isEnabled() is
        // false and the row must still be committed to the inbox.
        assertThat(notificationRepository.findAll().stream()
                .filter(row -> ACCOUNT.equals(row.getRecipientAccountId()))
                .count()).isEqualTo(1);
    }
}
