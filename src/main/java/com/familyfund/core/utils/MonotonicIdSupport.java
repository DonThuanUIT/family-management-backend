package com.familyfund.core.utils;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Thread-safe monotonic-plus-random 17-character ID generator helper.
 *
 * <p>Preserves the standard {@code PREFIX + 8-char Base36 timestamp + 6-char Crockford Base32}
 * layout while guaranteeing uniqueness across tight same-millisecond bursts: the 30-bit random
 * suffix increments monotonically within the same millisecond and rolls the logical timestamp
 * forward if the per-millisecond space is exhausted.</p>
 */
public final class MonotonicIdSupport {

    static final int TIMESTAMP_LENGTH = 8;
    static final int RANDOM_LENGTH = 6;
    static final int RADIX = 36;
    static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final int SUFFIX_SPACE = 1 << 30;
    static final int WRAP_SEED_BOUND = 1 << 29;

    private final String prefix;
    private final int maxLength;
    private final SecureRandom random = new SecureRandom();

    private long lastEpochMillis = -1L;
    private int lastSuffixValue = 0;

    public MonotonicIdSupport(String prefix, int maxLength) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        if (maxLength <= prefix.length()) {
            throw new IllegalArgumentException("maxLength must exceed prefix length");
        }
        this.prefix = prefix;
        this.maxLength = maxLength;
    }

    /**
     * Generates a monotonically unique candidate ID using the current wall-clock time.
     */
    public String generate() {
        return generate(System.currentTimeMillis());
    }

    synchronized String generate(long rawEpochMillis) {
        long epochMillis = Math.max(rawEpochMillis, 0L);
        if (epochMillis > lastEpochMillis) {
            lastEpochMillis = epochMillis;
            lastSuffixValue = random.nextInt(SUFFIX_SPACE);
        } else {
            lastSuffixValue++;
            if (lastSuffixValue >= SUFFIX_SPACE) {
                lastEpochMillis++;
                lastSuffixValue = random.nextInt(WRAP_SEED_BOUND);
            }
        }
        String timestamp = encodeTimestamp(lastEpochMillis);
        String suffix = encodeSuffix(lastSuffixValue);
        String candidate = prefix + timestamp + suffix;
        return candidate.length() <= maxLength ? candidate : candidate.substring(0, maxLength);
    }

    /**
     * Test hook for deterministic verification of wraparound near {@link #SUFFIX_SPACE}.
     */
    synchronized void seedForTesting(long epochMillis, int suffixValue) {
        this.lastEpochMillis = Math.max(epochMillis, 0L);
        this.lastSuffixValue = Math.floorMod(suffixValue, SUFFIX_SPACE);
    }

    private String encodeTimestamp(long epochMillis) {
        String encoded = Long.toString(epochMillis, RADIX).toUpperCase(Locale.ROOT);
        if (encoded.length() >= TIMESTAMP_LENGTH) {
            return encoded.substring(encoded.length() - TIMESTAMP_LENGTH);
        }
        return "0".repeat(TIMESTAMP_LENGTH - encoded.length()) + encoded;
    }

    private String encodeSuffix(int value) {
        char[] chars = new char[RANDOM_LENGTH];
        int remaining = value;
        for (int i = RANDOM_LENGTH - 1; i >= 0; i--) {
            chars[i] = ALPHABET.charAt(remaining & 0x1F);
            remaining >>>= 5;
        }
        return new String(chars);
    }
}
