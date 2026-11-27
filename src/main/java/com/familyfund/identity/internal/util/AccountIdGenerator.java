package com.familyfund.identity.internal.util;

import com.familyfund.core.utils.MonotonicIdSupport;
import org.springframework.stereotype.Component;

/**
 * Generator for globally unique `user_accounts.account_id` values (VARCHAR(20)).
 *
 * <p>Format: {@code ACC} + 8-char Base36 timestamp + 6-char Crockford Base32 suffix = 17 chars (&le; 20).</p>
 */
@Component
public class AccountIdGenerator {

    public static final String PREFIX = "ACC";
    public static final int MAX_LENGTH = 20;

    private final MonotonicIdSupport idSupport = new MonotonicIdSupport(PREFIX, MAX_LENGTH);

    /**
     * Generates a candidate account_id of at most 20 characters.
     */
    public String generate() {
        return idSupport.generate();
    }
}
