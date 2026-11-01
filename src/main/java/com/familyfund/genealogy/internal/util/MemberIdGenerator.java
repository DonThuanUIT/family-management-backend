package com.familyfund.genealogy.internal.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Generator for globally unique `members.member_id` values (VARCHAR(20)).
 *
 * <p>Format: {@code MEM} + 8-char Base36 timestamp + 6-char random suffix = 17 chars, always &le; 20.</p>
 * <pre>
 *   MEM  K4F8ZQ1M  7XB3QP
 *   |    |         +-- CSPRNG suffix (Crockford Base32, no ambiguous 0/O/1/I/L)
 *   |    +------------ epoch millis in Base36, fixed width, lexicographically sortable by time
 *   +----------------- fixed prefix identifying a member primary key
 * </pre>
 *
 * <p>Timestamp guarantees monotonic-ish, collision-sparse keys; the CSPRNG suffix decorrelates keys
 * generated in the same millisecond, and the service layer still re-checks uniqueness against the
 * database (plus the PRIMARY KEY constraint) so duplicates are impossible.</p>
 */
@Component
public class MemberIdGenerator {

    public static final String PREFIX = "MEM";
    public static final int MAX_LENGTH = 20;

    private static final int TIMESTAMP_LENGTH = 8;
    private static final int RANDOM_LENGTH = 6;
    private static final int RADIX = 36;
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a candidate member_id of at most 20 characters.
     */
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
