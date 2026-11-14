package com.familyfund.notification.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.dto.NotificationResponse;
import com.familyfund.notification.api.dto.UnreadCountResponse;
import com.familyfund.notification.internal.entity.Notification;
import com.familyfund.notification.internal.entity.NotificationDeviceToken;
import com.familyfund.notification.internal.push.PushMessage;
import com.familyfund.notification.internal.push.PushNotificationSender;
import com.familyfund.notification.internal.repository.NotificationDeviceTokenRepository;
import com.familyfund.notification.internal.repository.NotificationRepository;
import com.familyfund.notification.internal.util.NotificationKeyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of {@link NotificationServiceImpl} (US-39): the write path (persist + push), the self-scoped read
 * path (list / unread counter / mark as read) and device-token registration.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String CLAN = "CLAN_NTF_01";
    private static final String ACCOUNT = "ACC_NTF_01";
    private static final String OTHER_ACCOUNT = "ACC_NTF_02";
    private static final String NOTIFICATION_ID = "NTC_TEST_0001";
    private static final String TOKEN_ID = "DVT_TEST_0001";

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationDeviceTokenRepository deviceTokenRepository;
    @Mock
    private NotificationKeyGenerator keyGenerator;
    @Mock
    private PushNotificationSender pushNotificationSender;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private NotificationServiceImpl service;

    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ fixtures

    private NotificationCommand command() {
        return NotificationCommand.builder()
                .clanId(CLAN)
                .recipientAccountId(ACCOUNT)
                .category(NotificationCategory.Task)
                .title("Bạn được giao việc: Chuẩn bị mâm cỗ")
                .content("Sự kiện 'Giỗ tổ': công việc 'Chuẩn bị mâm cỗ' hạn chót 20/04/2026 10:00.")
                .referenceType("TASK")
                .referenceId("TSK_0001")
                .build();
    }

    private Notification notification(boolean read) {
        return Notification.builder()
                .notificationId(NOTIFICATION_ID)
                .clanId(CLAN)
                .recipientAccountId(ACCOUNT)
                .category(NotificationCategory.Task)
                .title("Tiêu đề")
                .content("Nội dung")
                .referenceType("TASK")
                .referenceId("TSK_0001")
                .isRead(read)
                .readAt(read ? Instant.parse("2026-04-20T09:00:00Z") : null)
                .createdAt(Instant.parse("2026-04-19T09:00:00Z"))
                .build();
    }

    private void stubSave() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubAuthenticated() {
        when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(ACCOUNT));
    }

    // ------------------------------------------------------------------ write path

    @Nested
    @DisplayName("notify()")
    class Notify {

        @Test
        @DisplayName("persists the notification with a generated id and returns it")
        void persistsWithGeneratedId() {
            when(keyGenerator.generateNotificationId()).thenReturn(NOTIFICATION_ID);
            stubSave();

            String id = service.notify(command());

            assertThat(id).isEqualTo(NOTIFICATION_ID);
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            Notification saved = captor.getValue();
            assertThat(saved.getNotificationId()).isEqualTo(NOTIFICATION_ID);
            assertThat(saved.getClanId()).isEqualTo(CLAN);
            assertThat(saved.getRecipientAccountId()).isEqualTo(ACCOUNT);
            assertThat(saved.getCategory()).isEqualTo(NotificationCategory.Task);
            assertThat(saved.getIsRead()).isFalse();
            assertThat(saved.getReadAt()).isNull();
            assertThat(saved.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("truncates a title longer than the 200-character column")
        void truncatesLongTitle() {
            when(keyGenerator.generateNotificationId()).thenReturn(NOTIFICATION_ID);
            stubSave();
            NotificationCommand longTitle = NotificationCommand.builder()
                    .clanId(CLAN).recipientAccountId(ACCOUNT).category(NotificationCategory.Event)
                    .title("A".repeat(260)).content("noi dung").build();

            service.notify(longTitle);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getTitle()).hasSize(NotificationServiceImpl.MAX_TITLE_LENGTH);
        }

        @Test
        @DisplayName("truncates referenceType/referenceId to their column widths")
        void truncatesReferenceFields() {
            when(keyGenerator.generateNotificationId()).thenReturn(NOTIFICATION_ID);
            stubSave();
            NotificationCommand longRefs = NotificationCommand.builder()
                    .clanId(CLAN).recipientAccountId(ACCOUNT).category(NotificationCategory.Finance)
                    .title("t").content("c")
                    .referenceType("R".repeat(45)).referenceId("9".repeat(45)).build();

            service.notify(longRefs);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getReferenceType()).hasSize(NotificationServiceImpl.MAX_REFERENCE_TYPE_LENGTH);
            assertThat(captor.getValue().getReferenceId()).hasSize(NotificationServiceImpl.MAX_REFERENCE_ID_LENGTH);
        }

        @Test
        @DisplayName("blank reference fields are stored as NULL")
        void blankReferencesBecomeNull() {
            when(keyGenerator.generateNotificationId()).thenReturn(NOTIFICATION_ID);
            stubSave();
            NotificationCommand blankRefs = NotificationCommand.builder()
                    .clanId(CLAN).recipientAccountId(ACCOUNT).category(NotificationCategory.Event)
                    .title("t").content("c").referenceType("   ").referenceId("").build();

            service.notify(blankRefs);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getReferenceType()).isNull();
            assertThat(captor.getValue().getReferenceId()).isNull();
        }

        @Test
        @DisplayName("rejects a null command")
        void rejectsNullCommand() {
            assertThatThrownBy(() -> service.notify(null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("không được để trống");
            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("rejects a missing clan id")
        void rejectsMissingClan() {
            NotificationCommand bad = NotificationCommand.builder()
                    .recipientAccountId(ACCOUNT).category(NotificationCategory.Task)
                    .title("t").content("c").build();

            assertThatThrownBy(() -> service.notify(bad))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Mã dòng họ");
        }

        @Test
        @DisplayName("rejects a missing recipient account")
        void rejectsMissingRecipient() {
            NotificationCommand bad = NotificationCommand.builder()
                    .clanId(CLAN).category(NotificationCategory.Task).title("t").content("c").build();

            assertThatThrownBy(() -> service.notify(bad))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Tài khoản nhận thông báo");
        }

        @Test
        @DisplayName("rejects a missing category")
        void rejectsMissingCategory() {
            NotificationCommand bad = NotificationCommand.builder()
                    .clanId(CLAN).recipientAccountId(ACCOUNT).title("t").content("c").build();

            assertThatThrownBy(() -> service.notify(bad))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Nhóm thông báo");
        }

        @Test
        @DisplayName("rejects a blank title")
        void rejectsBlankTitle() {
            NotificationCommand bad = NotificationCommand.builder()
                    .clanId(CLAN).recipientAccountId(ACCOUNT).category(NotificationCategory.Task)
                    .title("   ").content("c").build();

            assertThatThrownBy(() -> service.notify(bad))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Tiêu đề");
        }

        @Test
        @DisplayName("rejects blank content")
        void rejectsBlankContent() {
            NotificationCommand bad = NotificationCommand.builder()
                    .clanId(CLAN).recipientAccountId(ACCOUNT).category(NotificationCategory.Task)
                    .title("t").content("  ").build();

            assertThatThrownBy(() -> service.notify(bad))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Nội dung");
        }

        @Test
        @DisplayName("pushes to every active device token of the recipient")
        void pushesToActiveTokens() {
            when(keyGenerator.generateNotificationId()).thenReturn(NOTIFICATION_ID);
            stubSave();
            when(pushNotificationSender.isEnabled()).thenReturn(true);
            when(deviceTokenRepository.findActiveTokens(ACCOUNT)).thenReturn(List.of("TOKEN_A", "TOKEN_B"));
            when(pushNotificationSender.send(any(), any())).thenReturn(List.of());

            service.notify(command());

            ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
            verify(pushNotificationSender).send(eq(List.of("TOKEN_A", "TOKEN_B")), captor.capture());
            PushMessage message = captor.getValue();
            assertThat(message.title()).isEqualTo("Bạn được giao việc: Chuẩn bị mâm cỗ");
            assertThat(message.data())
                    .containsEntry("notificationId", NOTIFICATION_ID)
                    .containsEntry("category", "Task")
                    .containsEntry("referenceType", "TASK")
                    .containsEntry("referenceId", "TSK_0001");
        }

        @Test
        @DisplayName("skips push when the provider is disabled (log adapter)")
        void skipsPushWhenDisabled() {
            when(keyGenerator.generateNotificationId()).thenReturn(NOTIFICATION_ID);
            stubSave();
            when(pushNotificationSender.isEnabled()).thenReturn(false);

            service.notify(command());

            verify(deviceTokenRepository, never()).findActiveTokens(anyString());
            verify(pushNotificationSender, never()).send(any(), any());
        }

        @Test
        @DisplayName("skips push when the recipient has no active device token")
        void skipsPushWithoutTokens() {
            when(keyGenerator.generateNotificationId()).thenReturn(NOTIFICATION_ID);
            stubSave();
            when(pushNotificationSender.isEnabled()).thenReturn(true);
            when(deviceTokenRepository.findActiveTokens(ACCOUNT)).thenReturn(List.of());

            service.notify(command());

            verify(pushNotificationSender, never()).send(any(), any());
        }

        @Test
        @DisplayName("deactivates tokens Firebase reported as unregistered")
        void deactivatesInvalidTokens() {
            when(keyGenerator.generateNotificationId()).thenReturn(NOTIFICATION_ID);
            stubSave();
            when(pushNotificationSender.isEnabled()).thenReturn(true);
            when(deviceTokenRepository.findActiveTokens(ACCOUNT)).thenReturn(List.of("TOKEN_OLD", "TOKEN_OK"));
            when(pushNotificationSender.send(any(), any())).thenReturn(List.of("TOKEN_OLD"));
            NotificationDeviceToken stale = NotificationDeviceToken.builder()
                    .tokenId(TOKEN_ID).accountId(ACCOUNT).fcmToken("TOKEN_OLD")
                    .platform("Android").isActive(true).build();
            when(deviceTokenRepository.findByFcmToken("TOKEN_OLD")).thenReturn(Optional.of(stale));
            when(deviceTokenRepository.save(any(NotificationDeviceToken.class))).thenAnswer(inv -> inv.getArgument(0));

            service.notify(command());

            assertThat(stale.getIsActive()).isFalse();
            verify(deviceTokenRepository).save(stale);
        }

        @Test
        @DisplayName("a failing push never fails the persisted notification")
        void pushFailureIsSwallowed() {
            when(keyGenerator.generateNotificationId()).thenReturn(NOTIFICATION_ID);
            stubSave();
            when(pushNotificationSender.isEnabled()).thenReturn(true);
            when(deviceTokenRepository.findActiveTokens(ACCOUNT)).thenReturn(List.of("TOKEN_A"));
            when(pushNotificationSender.send(any(), any())).thenThrow(new IllegalStateException("FCM down"));

            String id = service.notify(command());

            assertThat(id).isEqualTo(NOTIFICATION_ID);
            verify(notificationRepository).save(any(Notification.class));
        }
    }

    // ------------------------------------------------------------------ read path

    @Nested
    @DisplayName("inbox queries")
    class Inbox {

        @Test
        @DisplayName("lists without filters through the account + clan scoped query")
        void listsWithoutFilters() {
            stubAuthenticated();
            Page<Notification> page = new PageImpl<>(List.of(notification(false)), pageable, 1);
            when(notificationRepository.findByRecipientAccountIdAndClanIdOrderByCreatedAtDesc(ACCOUNT, CLAN, pageable))
                    .thenReturn(page);

            Page<NotificationResponse> result = service.findMyNotifications(null, null, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            NotificationResponse dto = result.getContent().getFirst();
            assertThat(dto.notificationId()).isEqualTo(NOTIFICATION_ID);
            assertThat(dto.category()).isEqualTo(NotificationCategory.Task);
            assertThat(dto.isRead()).isFalse();
            assertThat(dto.clanId()).isEqualTo(CLAN);
        }

        @Test
        @DisplayName("filters by category only")
        void filtersByCategory() {
            stubAuthenticated();
            when(notificationRepository.findByRecipientAccountIdAndClanIdAndCategoryOrderByCreatedAtDesc(
                    ACCOUNT, CLAN, NotificationCategory.Finance, pageable))
                    .thenReturn(Page.empty(pageable));

            service.findMyNotifications(NotificationCategory.Finance, null, pageable);

            verify(notificationRepository).findByRecipientAccountIdAndClanIdAndCategoryOrderByCreatedAtDesc(
                    ACCOUNT, CLAN, NotificationCategory.Finance, pageable);
        }

        @Test
        @DisplayName("filters by read state only")
        void filtersByReadState() {
            stubAuthenticated();
            when(notificationRepository.findByRecipientAccountIdAndClanIdAndIsReadOrderByCreatedAtDesc(
                    ACCOUNT, CLAN, false, pageable))
                    .thenReturn(Page.empty(pageable));

            service.findMyNotifications(null, false, pageable);

            verify(notificationRepository).findByRecipientAccountIdAndClanIdAndIsReadOrderByCreatedAtDesc(
                    ACCOUNT, CLAN, false, pageable);
        }

        @Test
        @DisplayName("filters by category and read state together")
        void filtersByCategoryAndReadState() {
            stubAuthenticated();
            when(notificationRepository
                    .findByRecipientAccountIdAndClanIdAndCategoryAndIsReadOrderByCreatedAtDesc(
                            ACCOUNT, CLAN, NotificationCategory.Task, true, pageable))
                    .thenReturn(Page.empty(pageable));

            service.findMyNotifications(NotificationCategory.Task, true, pageable);

            verify(notificationRepository)
                    .findByRecipientAccountIdAndClanIdAndCategoryAndIsReadOrderByCreatedAtDesc(
                            ACCOUNT, CLAN, NotificationCategory.Task, true, pageable);
        }

        @Test
        @DisplayName("unread summary sums the three categories")
        void unreadSummarySumsCategories() {
            stubAuthenticated();
            when(notificationRepository.countByRecipientAccountIdAndClanIdAndCategoryAndIsReadFalse(
                    ACCOUNT, CLAN, NotificationCategory.Event)).thenReturn(2L);
            when(notificationRepository.countByRecipientAccountIdAndClanIdAndCategoryAndIsReadFalse(
                    ACCOUNT, CLAN, NotificationCategory.Task)).thenReturn(3L);
            when(notificationRepository.countByRecipientAccountIdAndClanIdAndCategoryAndIsReadFalse(
                    ACCOUNT, CLAN, NotificationCategory.Finance)).thenReturn(1L);

            UnreadCountResponse summary = service.unreadSummary();

            assertThat(summary.total()).isEqualTo(6L);
            assertThat(summary.byCategory().event()).isEqualTo(2L);
            assertThat(summary.byCategory().task()).isEqualTo(3L);
            assertThat(summary.byCategory().finance()).isEqualTo(1L);
        }

        @Test
        @DisplayName("markAsRead flips is_read and stamps read_at")
        void markAsReadStampsReadAt() {
            stubAuthenticated();
            Notification unread = notification(false);
            when(notificationRepository.findByNotificationIdAndRecipientAccountId(NOTIFICATION_ID, ACCOUNT))
                    .thenReturn(Optional.of(unread));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

            NotificationResponse result = service.markAsRead(NOTIFICATION_ID);

            assertThat(result.isRead()).isTrue();
            assertThat(result.readAt()).isNotNull();
            verify(notificationRepository).save(unread);
        }

        @Test
        @DisplayName("markAsRead on an already read notification keeps the original read_at and does not save")
        void markAsReadIsIdempotent() {
            stubAuthenticated();
            Notification read = notification(true);
            Instant originalReadAt = read.getReadAt();
            when(notificationRepository.findByNotificationIdAndRecipientAccountId(NOTIFICATION_ID, ACCOUNT))
                    .thenReturn(Optional.of(read));

            NotificationResponse result = service.markAsRead(NOTIFICATION_ID);

            assertThat(result.readAt()).isEqualTo(originalReadAt);
            verify(notificationRepository, never()).save(any(Notification.class));
        }

        @Test
        @DisplayName("markAsRead of another account's notification answers 404, never 403")
        void markAsReadForeignNotificationIsNotFound() {
            when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(OTHER_ACCOUNT));
            when(notificationRepository.findByNotificationIdAndRecipientAccountId(NOTIFICATION_ID, OTHER_ACCOUNT))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markAsRead(NOTIFICATION_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(NOTIFICATION_ID);
        }

        @Test
        @DisplayName("markAsRead rejects a blank id")
        void markAsReadRejectsBlankId() {
            stubAuthenticated();

            assertThatThrownBy(() -> service.markAsRead("  "))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("markAllAsRead without a category updates every unread row")
        void markAllAsReadAllCategories() {
            stubAuthenticated();
            when(notificationRepository.markAllUnreadAsRead(eq(ACCOUNT), eq(CLAN), any(Instant.class))).thenReturn(7);

            int updated = service.markAllAsRead(null);

            assertThat(updated).isEqualTo(7);
            verify(notificationRepository).markAllUnreadAsRead(eq(ACCOUNT), eq(CLAN), any(Instant.class));
        }

        @Test
        @DisplayName("markAllAsRead with a category only updates that category")
        void markAllAsReadOneCategory() {
            stubAuthenticated();
            when(notificationRepository.markAllUnreadAsReadByCategory(
                    eq(ACCOUNT), eq(CLAN), eq(NotificationCategory.Finance), any(Instant.class))).thenReturn(2);

            int updated = service.markAllAsRead(NotificationCategory.Finance);

            assertThat(updated).isEqualTo(2);
            verify(notificationRepository).markAllUnreadAsReadByCategory(
                    eq(ACCOUNT), eq(CLAN), eq(NotificationCategory.Finance), any(Instant.class));
        }

        @Test
        @DisplayName("an unauthenticated caller cannot read the inbox")
        void unauthenticatedCallerIsRejected() {
            when(currentUserProvider.currentAccountId()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findMyNotifications(null, null, pageable))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("tài khoản");
        }
    }

    // ------------------------------------------------------------------ device tokens

    @Nested
    @DisplayName("device tokens")
    class DeviceTokens {

        @Test
        @DisplayName("registers a new token for the caller")
        void registersNewToken() {
            stubAuthenticated();
            when(deviceTokenRepository.findByFcmToken("TOKEN_NEW")).thenReturn(Optional.empty());
            when(keyGenerator.generateDeviceTokenId()).thenReturn(TOKEN_ID);
            when(deviceTokenRepository.save(any(NotificationDeviceToken.class))).thenAnswer(inv -> inv.getArgument(0));

            String tokenId = service.registerDeviceToken("TOKEN_NEW", "IOS");

            assertThat(tokenId).isEqualTo(TOKEN_ID);
            ArgumentCaptor<NotificationDeviceToken> captor = ArgumentCaptor.forClass(NotificationDeviceToken.class);
            verify(deviceTokenRepository).save(captor.capture());
            NotificationDeviceToken saved = captor.getValue();
            assertThat(saved.getAccountId()).isEqualTo(ACCOUNT);
            assertThat(saved.getFcmToken()).isEqualTo("TOKEN_NEW");
            assertThat(saved.getPlatform()).isEqualTo("IOS");
            assertThat(saved.getIsActive()).isTrue();
            assertThat(saved.getLastSeenAt()).isNotNull();
        }

        @Test
        @DisplayName("re-registering an existing token moves it to the caller and reactivates it")
        void transfersExistingToken() {
            when(currentUserProvider.currentAccountId()).thenReturn(Optional.of(OTHER_ACCOUNT));
            NotificationDeviceToken existing = NotificationDeviceToken.builder()
                    .tokenId(TOKEN_ID).accountId(ACCOUNT).fcmToken("TOKEN_SHARED")
                    .platform("Android").isActive(false).build();
            when(deviceTokenRepository.findByFcmToken("TOKEN_SHARED")).thenReturn(Optional.of(existing));
            when(deviceTokenRepository.save(any(NotificationDeviceToken.class))).thenAnswer(inv -> inv.getArgument(0));

            String tokenId = service.registerDeviceToken("TOKEN_SHARED", "Web");

            assertThat(tokenId).isEqualTo(TOKEN_ID);
            assertThat(existing.getAccountId()).isEqualTo(OTHER_ACCOUNT);
            assertThat(existing.getPlatform()).isEqualTo("Web");
            assertThat(existing.getIsActive()).isTrue();
        }

        @Test
        @DisplayName("defaults a missing platform to Android")
        void defaultsPlatform() {
            stubAuthenticated();
            when(deviceTokenRepository.findByFcmToken("TOKEN_NEW")).thenReturn(Optional.empty());
            when(keyGenerator.generateDeviceTokenId()).thenReturn(TOKEN_ID);
            when(deviceTokenRepository.save(any(NotificationDeviceToken.class))).thenAnswer(inv -> inv.getArgument(0));

            service.registerDeviceToken("TOKEN_NEW", null);

            ArgumentCaptor<NotificationDeviceToken> captor = ArgumentCaptor.forClass(NotificationDeviceToken.class);
            verify(deviceTokenRepository).save(captor.capture());
            assertThat(captor.getValue().getPlatform()).isEqualTo("Android");
        }

        @Test
        @DisplayName("rejects an unsupported platform")
        void rejectsUnsupportedPlatform() {
            stubAuthenticated();

            assertThatThrownBy(() -> service.registerDeviceToken("TOKEN_NEW", "Blackberry"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Android");
            verifyNoInteractions(deviceTokenRepository);
        }

        @Test
        @DisplayName("rejects a token longer than the 255-character column")
        void rejectsTooLongToken() {
            stubAuthenticated();

            assertThatThrownBy(() -> service.registerDeviceToken("t".repeat(256), "Android"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("255");
        }

        @Test
        @DisplayName("rejects a blank token")
        void rejectsBlankToken() {
            stubAuthenticated();

            assertThatThrownBy(() -> service.registerDeviceToken("  ", "Android"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("unregister deactivates the caller's own token")
        void unregisterDeactivatesOwnToken() {
            stubAuthenticated();
            NotificationDeviceToken token = NotificationDeviceToken.builder()
                    .tokenId(TOKEN_ID).accountId(ACCOUNT).fcmToken("TOKEN_A")
                    .platform("Android").isActive(true).build();
            when(deviceTokenRepository.findByFcmToken("TOKEN_A")).thenReturn(Optional.of(token));
            when(deviceTokenRepository.save(any(NotificationDeviceToken.class))).thenAnswer(inv -> inv.getArgument(0));

            service.unregisterDeviceToken("TOKEN_A");

            assertThat(token.getIsActive()).isFalse();
            verify(deviceTokenRepository).save(token);
        }

        @Test
        @DisplayName("unregister ignores a token owned by another account")
        void unregisterIgnoresForeignToken() {
            stubAuthenticated();
            NotificationDeviceToken token = NotificationDeviceToken.builder()
                    .tokenId(TOKEN_ID).accountId(OTHER_ACCOUNT).fcmToken("TOKEN_B")
                    .platform("Android").isActive(true).build();
            when(deviceTokenRepository.findByFcmToken("TOKEN_B")).thenReturn(Optional.of(token));

            service.unregisterDeviceToken("TOKEN_B");

            assertThat(token.getIsActive()).isTrue();
            verify(deviceTokenRepository, never()).save(any(NotificationDeviceToken.class));
        }

        @Test
        @DisplayName("unregister of an unknown token is a silent no-op")
        void unregisterUnknownTokenIsNoOp() {
            stubAuthenticated();
            when(deviceTokenRepository.findByFcmToken("TOKEN_X")).thenReturn(Optional.empty());

            service.unregisterDeviceToken("TOKEN_X");

            verify(deviceTokenRepository, never()).save(any(NotificationDeviceToken.class));
        }

        @Test
        @DisplayName("push payload omits reference keys when the notification has none")
        void pushPayloadOmitsMissingReferences() {
            when(keyGenerator.generateNotificationId()).thenReturn(NOTIFICATION_ID);
            stubSave();
            when(pushNotificationSender.isEnabled()).thenReturn(true);
            when(deviceTokenRepository.findActiveTokens(ACCOUNT)).thenReturn(List.of("TOKEN_A"));
            when(pushNotificationSender.send(any(), any())).thenReturn(List.of());

            service.notify(NotificationCommand.builder()
                    .clanId(CLAN).recipientAccountId(ACCOUNT).category(NotificationCategory.Event)
                    .title("Sự kiện mới").content("Nội dung").build());

            ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
            verify(pushNotificationSender).send(eq(List.of("TOKEN_A")), captor.capture());
            assertThat(captor.getValue().data())
                    .containsEntry("category", "Event")
                    .doesNotContainKeys("referenceType", "referenceId");
        }
    }
}
