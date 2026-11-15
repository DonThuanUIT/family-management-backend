package com.familyfund.event.internal.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests of {@link AnniversaryKeyGenerator} (US-40): prefixes, the VARCHAR(20) bound and uniqueness.
 */
class AnniversaryKeyGeneratorTest {

    private final AnniversaryKeyGenerator generator = new AnniversaryKeyGenerator();

    @Test
    @DisplayName("reminder ids start with GIO and fit VARCHAR(20)")
    void reminderIdsFitColumn() {
        for (int i = 0; i < 100; i++) {
            String id = generator.generateReminderId();
            assertThat(id).startsWith(AnniversaryKeyGenerator.REMINDER_PREFIX);
            assertThat(id.length()).isLessThanOrEqualTo(AnniversaryKeyGenerator.MAX_LENGTH);
        }
    }

    @Test
    @DisplayName("suggestion ids start with GSU and fit VARCHAR(20)")
    void suggestionIdsFitColumn() {
        for (int i = 0; i < 100; i++) {
            String id = generator.generateSuggestionId();
            assertThat(id).startsWith(AnniversaryKeyGenerator.SUGGESTION_PREFIX);
            assertThat(id.length()).isLessThanOrEqualTo(AnniversaryKeyGenerator.MAX_LENGTH);
        }
    }

    @Test
    @DisplayName("ids generated back-to-back are unique")
    void idsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            ids.add(generator.generateReminderId());
        }
        assertThat(ids).hasSize(200);
    }
}
