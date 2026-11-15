package com.familyfund.event.internal.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Generator for `death_anniversary_reminders.reminder_id` (VARCHAR(20)) and for the synthetic keys of the US-40
 * suggestion payloads, which are not persisted rows and therefore only need to be unique per response.
 *
 * <p>Format: {@code PREFIX} + 8-char Base36 timestamp + 6-char Crockford Base32 suffix = 17 chars, always &le; 20.
 * Same scheme as `AlertIdGenerator` (US-08) so ids of all background workers look alike.</p>
 */
@Component
public class AnniversaryKeyGenerator {

    /** `death_anniversary_reminders.reminder_id`. */
    public static final String REMINDER_PREFIX = "GIO";
    /** Synthetic id of a suggested event in the US-40 payload (never persisted). */
    public static final String SUGGESTION_PREFIX = "GSU";

    public static final int MAX_LENGTH = 20;

    private static final int TIMESTAMP_LENGTH = 8;
    private static final int RANDOM_LENGTH = 6;
    private static final int RADIX = 36;
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();

    /** @return a candidate `reminder_id` of at most 20 characters. */
    public String generateReminderId() {
        return generate(REMINDER_PREFIX);
    }

    /** @return a synthetic suggestion id of at most 20 characters. */
    public String generateSuggestionId() {
        return generate(SUGGESTION_PREFIX);
    }

    private String generate(String prefix) {
        String candidate = prefix + encodeTimestamp(System.currentTimeMillis()) + randomSuffix();
        return candidate.length() <= MAX_LENGTH ? candidate : candidate.substring(0, MAX_LENGTH);
    }

    private String encodeTimestamp(long epochMillis) {
        String encoded = Long.toString(Math.max(epochMillis, 0L), RADIX).toUpperCase(Locale.ROOT);
        if (encoded.length() >= TIMESTAMP_LENGTH) {
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
