package com.familyfund.notification.internal.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link NotificationKeyGenerator}: prefix, length bound of the VARCHAR(20) columns and uniqueness.
 */
class NotificationKeyGeneratorTest {

    private final NotificationKeyGenerator generator = new NotificationKeyGenerator();

    @Test
    @DisplayName("notification ids start with NTC and never exceed the 20-character column")
    void notificationIdsFitColumn() {
        for (int i = 0; i < 200; i++) {
            String id = generator.generateNotificationId();
            assertThat(id).startsWith(NotificationKeyGenerator.NOTIFICATION_PREFIX);
            assertThat(id.length()).isLessThanOrEqualTo(NotificationKeyGenerator.MAX_LENGTH);
        }
    }

    @Test
    @DisplayName("device token ids start with DVT and never exceed the 20-character column")
    void tokenIdsFitColumn() {
        for (int i = 0; i < 200; i++) {
            String id = generator.generateDeviceTokenId();
            assertThat(id).startsWith(NotificationKeyGenerator.DEVICE_TOKEN_PREFIX);
            assertThat(id.length()).isLessThanOrEqualTo(NotificationKeyGenerator.MAX_LENGTH);
        }
    }

    @Test
    @DisplayName("200 ids generated back-to-back are unique")
    void idsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            ids.add(generator.generateNotificationId());
        }
        assertThat(ids).hasSize(200);
    }
}
