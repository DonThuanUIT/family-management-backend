package com.familyfund.core.audit.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Generator for globally unique `audit_logs.audit_id` values (VARCHAR(20)).
 */
@Component("systemAuditIdGenerator")
public class AuditIdGenerator {

    public static final String PREFIX = "AUD";
    public static final int MAX_LENGTH = 20;

    private static final int TIMESTAMP_LENGTH = 8;
    private static final int RANDOM_LENGTH = 6;
    private static final int RADIX = 36;
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        String timestamp = encodeTimestamp(System.currentTimeMillis());
        String suffix = randomSuffix();
        String candidate = PREFIX + timestamp + suffix;
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
