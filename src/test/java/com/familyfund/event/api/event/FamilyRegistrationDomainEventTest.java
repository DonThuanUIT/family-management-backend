package com.familyfund.event.api.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Family Registration Domain Events Test")
class FamilyRegistrationDomainEventTest {

    @Test
    @DisplayName("FamilyRegistrationSubmittedDomainEvent holds correct properties")
    void testSubmittedDomainEvent() {
        Instant now = Instant.now();
        FamilyRegistrationSubmittedDomainEvent event = new FamilyRegistrationSubmittedDomainEvent(
                "REG_001",
                "EVT_001",
                "CLAN_NGUYEN",
                "MEM_001",
                5,
                1,
                4,
                now,
                "ACC_001"
        );

        assertEquals("REG_001", event.registrationId());
        assertEquals("EVT_001", event.eventId());
        assertEquals("CLAN_NGUYEN", event.clanId());
        assertEquals("MEM_001", event.representativeMemberId());
        assertEquals(5, event.totalAttendees());
        assertEquals(1, event.vegetarianMeals());
        assertEquals(4, event.savoryMeals());
        assertEquals(now, event.submittedAt());
        assertEquals("ACC_001", event.triggeredBy());
    }

    @Test
    @DisplayName("RegistrationModificationRequestedDomainEvent holds correct properties")
    void testModificationRequestedDomainEvent() {
        Instant now = Instant.now();
        RegistrationModificationRequestedDomainEvent event = new RegistrationModificationRequestedDomainEvent(
                "REG_001",
                "EVT_001",
                "CLAN_NGUYEN",
                "MEM_001",
                "MODIFICATION",
                "Bận việc gia đình, xin giảm từ 5 xuống 3 suất",
                now,
                "ACC_001"
        );

        assertEquals("REG_001", event.registrationId());
        assertEquals("MODIFICATION", event.modificationType());
        assertEquals("Bận việc gia đình, xin giảm từ 5 xuống 3 suất", event.reason());
        assertEquals(now, event.requestedAt());
    }

    @Test
    @DisplayName("RegistrationReviewedDomainEvent holds approval details")
    void testReviewedDomainEvent() {
        Instant now = Instant.now();
        RegistrationReviewedDomainEvent event = new RegistrationReviewedDomainEvent(
                "REG_001",
                "EVT_001",
                "CLAN_NGUYEN",
                "MEM_001",
                true,
                "ACC_LEADER",
                null,
                now
        );

        assertTrue(event.approved());
        assertEquals("ACC_LEADER", event.reviewerAccountId());
        assertNull(event.rejectionReason());
    }
}
