package com.familyfund.event.internal.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AssignTaskRequest Bean Validation Test")
class AssignTaskRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Request with assignedMemberId should pass validation")
    void testWithMemberOnly() {
        AssignTaskRequest request = new AssignTaskRequest(null, "MEM_001");
        Set<ConstraintViolation<AssignTaskRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Assigning to member should pass validation");
    }

    @Test
    @DisplayName("Request with assignedBranchId should pass validation")
    void testWithBranchOnly() {
        AssignTaskRequest request = new AssignTaskRequest("BR_CHI_2", null);
        Set<ConstraintViolation<AssignTaskRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Assigning to branch should pass validation");
    }

    @Test
    @DisplayName("Request with both member and branch should pass validation")
    void testWithBothMemberAndBranch() {
        AssignTaskRequest request = new AssignTaskRequest("BR_CHI_2", "MEM_MAI_ANH");
        Set<ConstraintViolation<AssignTaskRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Assigning to both branch and member should pass validation");
    }

    @Test
    @DisplayName("Request with neither member nor branch should fail validation")
    void testWithNoAssignee() {
        AssignTaskRequest request = new AssignTaskRequest(null, null);
        Set<ConstraintViolation<AssignTaskRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Phải chỉ định ít nhất cá nhân hoặc nhánh gia đình phụ trách")));
    }

    @Test
    @DisplayName("Request with blank strings should fail validation")
    void testWithBlankAssignees() {
        AssignTaskRequest request = new AssignTaskRequest("   ", "   ");
        Set<ConstraintViolation<AssignTaskRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Phải chỉ định ít nhất cá nhân hoặc nhánh gia đình phụ trách")));
    }
}

