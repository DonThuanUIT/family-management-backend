package com.familyfund.notification.internal.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Generator for the primary keys of the notification module (both VARCHAR(20)).
 *
 * <p>Format: {@code PREFIX} + 8-char Base36 timestamp + 6-char random suffix = 17 chars, always &le; 20.
 * The timestamp keeps ids sortable by creation time, the CSPRNG suffix decorrelates ids created in the same
 * millisecond, and the service still relies on the PRIMARY KEY as the final guard.</p>
 *
 * <ul>
 *   <li>{@code NTC} — {@code notifications.notification_id}</li>
 *   <li>{@code DVT} — {@code notification_device_tokens.token_id}</li>
 * </ul>
 */
@Component
public class NotificationKeyGenerator {

    public static final String NOTIFICATION_PREFIX = "NTC";
    public static final String DEVICE_TOKEN_PREFIX = "DVT";
    public static final int MAX_LENGTH = 20;

    private static final int TIMESTAMP_LENGTH = 8;
    private static final int RANDOM_LENGTH = 6;
    private static final int RADIX = 36;
    /** Crockford Base32 alphabet: unambiguous characters only. */
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();

    /** @return a candidate {@code notifications.notification_id} of at most 20 characters. */
    public String generateNotificationId() {
        return generate(NOTIFICATION_PREFIX);
    }

    /** @return a candidate {@code notification_device_tokens.token_id} of at most 20 characters. */
    public String generateDeviceTokenId() {
        return generate(DEVICE_TOKEN_PREFIX);
    }

    private String generate(String prefix) {
        String candidate = prefix + encodeTimestamp(System.currentTimeMillis()) + randomSuffix();
        return candidate.length() <= MAX_LENGTH ? candidate : candidate.substring(0, MAX_LENGTH);
    }

    private String encodeTimestamp(long epochMillis) {
        String encoded = Long.toString(Math.max(epochMillis, 0L), RADIX).toUpperCase(Locale.ROOT);
        if (encoded.length() >= TIMESTAMP_LENGTH) {
            // Far-future safety: keep the least significant digits so total length stays bounded.
            return encoded.substring(encoded.length() - TIMESTAMP_LENGTH);
        }
        return "0".repeat(TIMESTAMP_LENGTH - encoded.length()) + encoded;
    }

    private String randomSuffix() {
        char[] chars = new char[RANDOM_LENGTH];
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(chars);
    }
}
