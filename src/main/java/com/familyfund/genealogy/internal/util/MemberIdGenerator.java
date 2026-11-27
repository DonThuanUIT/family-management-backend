package com.familyfund.genealogy.internal.util;

import com.familyfund.core.utils.MonotonicIdSupport;
import org.springframework.stereotype.Component;

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

    private final MonotonicIdSupport idSupport = new MonotonicIdSupport(PREFIX, MAX_LENGTH);

    /**
     * Generates a candidate member_id of at most 20 characters.
     */
    public String generate() {
        return idSupport.generate();
    }
}
