package com.familyfund.event.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RegistrationStatus Enum Test")
class RegistrationStatusTest {

    @Test
    @DisplayName("Verify all required RegistrationStatus enum constants exist matching DB CHECK constraints")
    void testEnumConstants() {
        RegistrationStatus[] values = RegistrationStatus.values();
        assertEquals(4, values.length);
        assertNotNull(RegistrationStatus.valueOf("Confirmed"));
        assertNotNull(RegistrationStatus.valueOf("PendingModification"));
        assertNotNull(RegistrationStatus.valueOf("PendingCancellation"));
        assertNotNull(RegistrationStatus.valueOf("Cancelled"));
    }

    @Test
    @DisplayName("isCancelled returns true only for Cancelled status")
    void testIsCancelled() {
        assertFalse(RegistrationStatus.Confirmed.isCancelled());
        assertFalse(RegistrationStatus.PendingModification.isCancelled());
        assertFalse(RegistrationStatus.PendingCancellation.isCancelled());
        assertTrue(RegistrationStatus.Cancelled.isCancelled());
    }

    @Test
    @DisplayName("isPendingReview returns true only for pending modification or cancellation")
    void testIsPendingReview() {
        assertFalse(RegistrationStatus.Confirmed.isPendingReview());
        assertTrue(RegistrationStatus.PendingModification.isPendingReview());
        assertTrue(RegistrationStatus.PendingCancellation.isPendingReview());
        assertFalse(RegistrationStatus.Cancelled.isPendingReview());
    }
}
