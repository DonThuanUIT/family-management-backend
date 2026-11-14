package com.familyfund.notification.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.notification.api.NotificationCategory;
import com.familyfund.notification.api.NotificationCommand;
import com.familyfund.notification.api.NotificationService;
import com.familyfund.notification.api.dto.NotificationResponse;
import com.familyfund.notification.api.dto.UnreadCountResponse;
import com.familyfund.notification.internal.entity.Notification;
import com.familyfund.notification.internal.entity.NotificationDeviceToken;
import com.familyfund.notification.internal.push.PushMessage;
import com.familyfund.notification.internal.push.PushNotificationSender;
import com.familyfund.notification.internal.repository.NotificationDeviceTokenRepository;
import com.familyfund.notification.internal.repository.NotificationRepository;
import com.familyfund.notification.internal.util.NotificationKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link NotificationService} (US-39).
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li><b>Write path</b> — {@link #notify(NotificationCommand)} saves the row first and pushes afterwards. The
 *       push is wrapped in its own try/catch so an unreachable push provider can never lose the notification: the
 *       inbox is the source of truth, push is best effort.</li>
 *   <li><b>Read path</b> — every method resolves the caller from {@link CurrentUserProvider} and filters by
 *       {@code recipient_account_id} + {@link TenantContext}, so an account can never read or mutate another
 *       account's inbox; a foreign id answers 404 rather than 403 so ids cannot be probed.</li>
 *   <li><b>Device tokens</b> — {@code fcm_token} is UNIQUE, so registering an existing token transfers it to the
 *       caller (shared device / re-login) instead of creating a duplicate.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** notifications.title is VARCHAR(200). */
    static final int MAX_TITLE_LENGTH = 200;
    /** notifications.reference_type is VARCHAR(30). */
    static final int MAX_REFERENCE_TYPE_LENGTH = 30;
    /** notifications.reference_id is VARCHAR(20). */
    static final int MAX_REFERENCE_ID_LENGTH = 20;
    /** notification_device_tokens.fcm_token is VARCHAR(255). */
    static final int MAX_DEVICE_TOKEN_LENGTH = 255;
    /** notification_device_tokens.platform CHECK constraint. */
    static final Set<String> SUPPORTED_PLATFORMS = Set.of("Android", "IOS", "Web");

    private final NotificationRepository notificationRepository;
    private final NotificationDeviceTokenRepository deviceTokenRepository;
    private final NotificationKeyGenerator keyGenerator;
    private final PushNotificationSender pushNotificationSender;
    private final CurrentUserProvider currentUserProvider;

    // ------------------------------------------------------------------ write path

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String notify(NotificationCommand command) {
        Notification notification = buildNotification(command);
        Notification saved = notificationRepository.save(notification);
        pushQuietly(saved);
        log.debug("Notification {} saved for account {} in category {}",
                saved.getNotificationId(), saved.getRecipientAccountId(), saved.getCategory());
        return saved.getNotificationId();
    }

    private Notification buildNotification(NotificationCommand command) {
        if (command == null) {
            throw new BadRequestException("Nội dung thông báo không được để trống");
        }
        String clanId = requireText(command.clanId(), "Mã dòng họ của thông báo không được để trống");
        String recipientAccountId = requireText(command.recipientAccountId(),
                "Tài khoản nhận thông báo không được để trống");
        if (command.category() == null) {
            throw new BadRequestException("Nhóm thông báo không được để trống");
        }
        String title = truncate(requireText(command.title(), "Tiêu đề thông báo không được để trống"),
                MAX_TITLE_LENGTH);
        String content = requireText(command.content(), "Nội dung thông báo không được để trống");

        return Notification.builder()
                .notificationId(keyGenerator.generateNotificationId())
                .clanId(clanId)
                .recipientAccountId(recipientAccountId)
                .category(command.category())
                .title(title)
                .content(content)
                .referenceType(truncateOrNull(command.referenceType(), MAX_REFERENCE_TYPE_LENGTH))
                .referenceId(truncateOrNull(command.referenceId(), MAX_REFERENCE_ID_LENGTH))
                .isRead(Boolean.FALSE)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Pushes an already-persisted notification to the recipient's devices.
     *
     * <p>Never throws: a push problem must not roll back the notification. Tokens Firebase reports as
     * unregistered are deactivated so the next push does not retry them.</p>
     */
    private void pushQuietly(Notification notification) {
        try {
            if (!pushNotificationSender.isEnabled()) {
                log.debug("Push provider '{}' is disabled; notification {} stays inbox-only",
                        pushNotificationSender.providerName(), notification.getNotificationId());
                return;
            }
            List<String> tokens = deviceTokenRepository.findActiveTokens(notification.getRecipientAccountId());
            if (tokens.isEmpty()) {
                log.debug("No active device token for account {}; notification {} stays inbox-only",
                        notification.getRecipientAccountId(), notification.getNotificationId());
                return;
            }

            Map<String, String> data = new HashMap<>();
            data.put("notificationId", notification.getNotificationId());
            data.put("category", notification.getCategory().name());
            if (notification.getReferenceType() != null) {
                data.put("referenceType", notification.getReferenceType());
            }
            if (notification.getReferenceId() != null) {
                data.put("referenceId", notification.getReferenceId());
            }

            PushMessage message = new PushMessage(notification.getTitle(), notification.getContent(), data);
            List<String> invalidTokens = pushNotificationSender.send(tokens, message);
            deactivateTokens(notification.getRecipientAccountId(), invalidTokens);
        } catch (RuntimeException ex) {
            log.warn("Push failed for notification {}: {}", notification.getNotificationId(), ex.getMessage());
        }
    }

    private void deactivateTokens(String accountId, List<String> invalidTokens) {
        if (invalidTokens == null || invalidTokens.isEmpty()) {
            return;
        }
        for (String token : invalidTokens) {
            deviceTokenRepository.findByFcmToken(token).ifPresent(entity -> {
                if (Boolean.TRUE.equals(entity.getIsActive())) {
                    entity.setIsActive(Boolean.FALSE);
                    deviceTokenRepository.save(entity);
                    log.info("Deactivated invalid FCM token {} of account {}", entity.getTokenId(), accountId);
                }
            });
        }
    }

    // ------------------------------------------------------------------ read path

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> findMyNotifications(NotificationCategory category,
                                                          Boolean isRead,
                                                          Pageable pageable) {
        String accountId = requireCurrentAccountId();
        String clanId = TenantContext.getClanId();
        Page<Notification> page;
        if (category != null && isRead != null) {
            page = notificationRepository.findByRecipientAccountIdAndClanIdAndCategoryAndIsReadOrderByCreatedAtDesc(
                    accountId, clanId, category, isRead, pageable);
        } else if (category != null) {
            page = notificationRepository.findByRecipientAccountIdAndClanIdAndCategoryOrderByCreatedAtDesc(
                    accountId, clanId, category, pageable);
        } else if (isRead != null) {
            page = notificationRepository.findByRecipientAccountIdAndClanIdAndIsReadOrderByCreatedAtDesc(
                    accountId, clanId, isRead, pageable);
        } else {
            page = notificationRepository.findByRecipientAccountIdAndClanIdOrderByCreatedAtDesc(
                    accountId, clanId, pageable);
        }
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadSummary() {
        String accountId = requireCurrentAccountId();
        String clanId = TenantContext.getClanId();
        long event = notificationRepository.countByRecipientAccountIdAndClanIdAndCategoryAndIsReadFalse(
                accountId, clanId, NotificationCategory.Event);
        long task = notificationRepository.countByRecipientAccountIdAndClanIdAndCategoryAndIsReadFalse(
                accountId, clanId, NotificationCategory.Task);
        long finance = notificationRepository.countByRecipientAccountIdAndClanIdAndCategoryAndIsReadFalse(
                accountId, clanId, NotificationCategory.Finance);
        return new UnreadCountResponse(event + task + finance, new UnreadCountResponse.CategoryCount(event, task, finance));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationResponse markAsRead(String notificationId) {
        String accountId = requireCurrentAccountId();
        String id = requireText(notificationId, "Mã thông báo không được để trống");
        Notification notification = notificationRepository
                .findByNotificationIdAndRecipientAccountId(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Thông báo", "notificationId", id));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(Boolean.TRUE);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllAsRead(NotificationCategory category) {
        String accountId = requireCurrentAccountId();
        String clanId = TenantContext.getClanId();
        Instant readAt = Instant.now();
        int updated = category == null
                ? notificationRepository.markAllUnreadAsRead(accountId, clanId, readAt)
                : notificationRepository.markAllUnreadAsReadByCategory(accountId, clanId, category, readAt);
        log.debug("Marked {} notification(s) as read for account {}", updated, accountId);
        return updated;
    }

    // ------------------------------------------------------------------ device tokens

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String registerDeviceToken(String fcmToken, String platform) {
        String accountId = requireCurrentAccountId();
        String token = requireText(fcmToken, "FCM token không được để trống");
        if (token.length() > MAX_DEVICE_TOKEN_LENGTH) {
            throw new BadRequestException("FCM token không được vượt quá " + MAX_DEVICE_TOKEN_LENGTH + " ký tự");
        }
        String normalizedPlatform = normalizePlatform(platform);

        NotificationDeviceToken entity = deviceTokenRepository.findByFcmToken(token)
                .orElseGet(() -> NotificationDeviceToken.builder()
                        .tokenId(keyGenerator.generateDeviceTokenId())
                        .fcmToken(token)
                        .accountId(accountId)
                        .build());

        // A device token belongs to the account that registered it last: shared devices and re-logins move it.
        entity.setAccountId(accountId);
        entity.setPlatform(normalizedPlatform);
        entity.setIsActive(Boolean.TRUE);
        entity.setLastSeenAt(Instant.now());
        NotificationDeviceToken saved = deviceTokenRepository.save(entity);
        log.debug("Device token {} registered for account {} ({})", saved.getTokenId(), accountId, normalizedPlatform);
        return saved.getTokenId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unregisterDeviceToken(String fcmToken) {
        String accountId = requireCurrentAccountId();
        String token = requireText(fcmToken, "FCM token không được để trống");
        deviceTokenRepository.findByFcmToken(token).ifPresentOrElse(entity -> {
            if (!accountId.equals(entity.getAccountId())) {
                log.warn("Account {} tried to unregister device token {} owned by another account; ignored",
                        accountId, entity.getTokenId());
                return;
            }
            entity.setIsActive(Boolean.FALSE);
            deviceTokenRepository.save(entity);
            log.debug("Device token {} deactivated for account {}", entity.getTokenId(), accountId);
        }, () -> log.debug("Unregister called for unknown device token; ignored"));
    }

    // ------------------------------------------------------------------ helpers

    private String requireCurrentAccountId() {
        return currentUserProvider.currentAccountId()
                .orElseThrow(() -> new BadRequestException("Không xác định được tài khoản đang đăng nhập"));
    }

    private static String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return NotificationDeviceToken.PLATFORM_ANDROID;
        }
        String trimmed = platform.trim();
        for (String supported : SUPPORTED_PLATFORMS) {
            if (supported.equalsIgnoreCase(trimmed)) {
                return supported;
            }
        }
        throw new BadRequestException("Nền tảng thiết bị phải là một trong: Android, IOS, Web");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String truncateOrNull(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return truncate(value.trim(), maxLength);
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .clanId(notification.getClanId())
                .category(notification.getCategory())
                .title(notification.getTitle())
                .content(notification.getContent())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
