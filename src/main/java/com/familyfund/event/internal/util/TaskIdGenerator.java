package com.familyfund.event.internal.util;

import com.familyfund.core.utils.MonotonicIdSupport;
import org.springframework.stereotype.Component;

/**
 * Generator for globally unique `event_tasks.task_id` values (VARCHAR(20)).
 *
 * <p>Format: {@code TSK} + 8-char Base36 timestamp + 6-char Crockford Base32 suffix = 17 chars, always &le; 20.</p>
 */
@Component
public class TaskIdGenerator {

    public static final String PREFIX = "TSK";
    public static final int MAX_LENGTH = 20;

    private final MonotonicIdSupport idSupport = new MonotonicIdSupport(PREFIX, MAX_LENGTH);

    /**
     * Generates a candidate task_id of at most 20 characters.
     */
    public String generate() {
        return idSupport.generate();
    }
}

