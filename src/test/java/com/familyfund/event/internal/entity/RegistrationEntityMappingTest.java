package com.familyfund.event.internal.entity;

import com.familyfund.event.api.dto.RegistrationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Registration Entity & Composite Key Mapping Test")
class RegistrationEntityMappingTest {

    @Test
    @DisplayName("FamilyRegistration builder and prePersist set correct defaults")
    void testFamilyRegistrationDefaults() {
        FamilyRegistration reg = FamilyRegistration.builder()
                .registrationId("REG1234567890ABCDE")
                .eventId("EVT_TEST_1")
                .representativeMemberId("MEM_001")
                .totalAttendees(4)
                .build();

        reg.prePersist();

        assertEquals(0, reg.getVegetarianMeals());
        assertEquals(0, reg.getSavoryMeals());
        assertEquals(RegistrationStatus.Confirmed, reg.getStatus());
        assertNotNull(reg.getRegistrationDate());
        assertNotNull(reg.getUpdatedAt());
        assertNotNull(reg.getMemberDetails());
        assertTrue(reg.getMemberDetails().isEmpty());
    }

    @Test
    @DisplayName("RegistrationMemberDetailId equals and hashCode contract")
    void testCompositeKeyEqualsAndHashCode() {
        RegistrationMemberDetailId id1 = new RegistrationMemberDetailId("REG_01", "MEM_01");
        RegistrationMemberDetailId id2 = new RegistrationMemberDetailId("REG_01", "MEM_01");
        RegistrationMemberDetailId id3 = new RegistrationMemberDetailId("REG_01", "MEM_02");

        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertEquals(id1.hashCode(), id2.hashCode());
        assertNotEquals(id1.hashCode(), id3.hashCode());
    }

    @Test
    @DisplayName("RegistrationMemberDetail prePersist sets isCheckedIn to false if null")
    void testRegistrationMemberDetailPrePersist() {
        RegistrationMemberDetail detail = RegistrationMemberDetail.builder()
                .registrationId("REG_01")
                .memberId("MEM_01")
                .isCheckedIn(null)
                .build();

        detail.prePersist();

        assertFalse(detail.getIsCheckedIn());
    }

    @Test
    @DisplayName("Event registrations list bidirectional helper")
    void testEventRegistrationAssociation() {
        Event event = Event.builder()
                .eventId("EVT_100")
                .eventName("Giỗ Tổ Họ Nguyễn")
                .registrations(new ArrayList<>())
                .build();

        FamilyRegistration reg = FamilyRegistration.builder()
                .registrationId("REG_100")
                .eventId("EVT_100")
                .event(event)
                .totalAttendees(5)
                .build();

        event.getRegistrations().add(reg);

        assertEquals(1, event.getRegistrations().size());
        assertEquals("REG_100", event.getRegistrations().get(0).getRegistrationId());
    }
}

