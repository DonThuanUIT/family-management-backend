package com.familyfund.event.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TaskStatus Enum & Transition Rules Test")
class TaskStatusTest {

    @Test
    @DisplayName("Verify all required TaskStatus enum constants exist matching DB CHECK constraints")
    void testEnumConstants() {
        TaskStatus[] values = TaskStatus.values();
        assertEquals(4, values.length);
        assertNotNull(TaskStatus.valueOf("Unassigned"));
        assertNotNull(TaskStatus.valueOf("InProgress"));
        assertNotNull(TaskStatus.valueOf("Completed"));
        assertNotNull(TaskStatus.valueOf("Overdue"));
    }

    @Test
    @DisplayName("Unassigned can transition to InProgress when assigned")
    void testUnassignedTransitions() {
        assertTrue(TaskStatus.Unassigned.canTransitionTo(TaskStatus.InProgress));
        assertFalse(TaskStatus.Unassigned.canTransitionTo(TaskStatus.Completed));
        assertFalse(TaskStatus.Unassigned.canTransitionTo(TaskStatus.Overdue));
    }

    @Test
    @DisplayName("InProgress can transition to Completed, Overdue, or Unassigned")
    void testInProgressTransitions() {
        assertTrue(TaskStatus.InProgress.canTransitionTo(TaskStatus.Completed));
        assertTrue(TaskStatus.InProgress.canTransitionTo(TaskStatus.Overdue));
        assertTrue(TaskStatus.InProgress.canTransitionTo(TaskStatus.Unassigned));
    }

    @Test
    @DisplayName("Overdue can transition to Completed or InProgress")
    void testOverdueTransitions() {
        assertTrue(TaskStatus.Overdue.canTransitionTo(TaskStatus.Completed));
        assertTrue(TaskStatus.Overdue.canTransitionTo(TaskStatus.InProgress));
        assertFalse(TaskStatus.Overdue.canTransitionTo(TaskStatus.Unassigned));
    }

    @Test
    @DisplayName("Completed can transition back to InProgress if re-opened")
    void testCompletedTransitions() {
        assertTrue(TaskStatus.Completed.canTransitionTo(TaskStatus.InProgress));
        assertFalse(TaskStatus.Completed.canTransitionTo(TaskStatus.Unassigned));
        assertFalse(TaskStatus.Completed.canTransitionTo(TaskStatus.Overdue));
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    @DisplayName("Same status transition is always allowed (idempotent)")
    void testSameStatusTransition(TaskStatus status) {
        assertTrue(status.canTransitionTo(status));
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    @DisplayName("Transition to null is always rejected")
    void testNullTransition(TaskStatus status) {
        assertFalse(status.canTransitionTo(null));
    }
}

