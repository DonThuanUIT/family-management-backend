package com.familyfund.event.api.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TaskAssignedDomainEvent Record Test")
class TaskAssignedDomainEventTest {

    @Test
    @DisplayName("TaskAssignedDomainEvent holds all expected payload fields")
    void testRecordFields() {
        Instant deadline = Instant.now().plusSeconds(86400);
        TaskAssignedDomainEvent event = new TaskAssignedDomainEvent(
                "EVT_100",
                "CLAN_DEFAULT",
                "Lễ Giỗ Tổ Mùa Thu",
                "TSK_200",
                "Mua sắm lễ vật",
                "MEM_001",
                "BR_CHI_2",
                BigDecimal.valueOf(5000000),
                deadline,
                "ACC_ADMIN"
        );

        assertEquals("EVT_100", event.eventId());
        assertEquals("CLAN_DEFAULT", event.clanId());
        assertEquals("Lễ Giỗ Tổ Mùa Thu", event.eventName());
        assertEquals("TSK_200", event.taskId());
        assertEquals("Mua sắm lễ vật", event.taskName());
        assertEquals("MEM_001", event.assignedMemberId());
        assertEquals("BR_CHI_2", event.assignedBranchId());
        assertEquals(BigDecimal.valueOf(5000000), event.advanceBudget());
        assertEquals(deadline, event.deadline());
        assertEquals("ACC_ADMIN", event.triggeredBy());
    }
}

