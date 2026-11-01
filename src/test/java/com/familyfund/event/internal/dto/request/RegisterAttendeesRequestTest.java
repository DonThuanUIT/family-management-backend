package com.familyfund.event.internal.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RegisterAttendeesRequest Bean Validation Test")
class RegisterAttendeesRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid request passes validation and applies default meal values")
    void testValidRequest() {
        RegisterAttendeesRequest request = new RegisterAttendeesRequest(
                "MEM_001",
                4,
                1,
                3,
                "Ăn chay 1 người",
                "Đi xe ô tô gia đình",
                List.of("MEM_001", "MEM_002")
        );

        Set<ConstraintViolation<RegisterAttendeesRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should produce no violations");
        assertEquals(1, request.vegetarianMeals());
        assertEquals(3, request.savoryMeals());
    }

    @Test
    @DisplayName("Default meal counts are 0 when passed as null")
    void testDefaultMealCounts() {
        RegisterAttendeesRequest request = new RegisterAttendeesRequest(
                null,
                2,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(0, request.vegetarianMeals());
        assertEquals(0, request.savoryMeals());
        assertNotNull(request.attendingMemberIds());
        assertTrue(request.attendingMemberIds().isEmpty());

        Set<ConstraintViolation<RegisterAttendeesRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("totalAttendees cannot be null or less than 1")
    void testTotalAttendeesValidation() {
        RegisterAttendeesRequest nullAttendees = new RegisterAttendeesRequest(
                "MEM_001",
                null,
                0,
                0,
                null,
                null,
                null
        );

        Set<ConstraintViolation<RegisterAttendeesRequest>> violationsNull = validator.validate(nullAttendees);
        assertFalse(violationsNull.isEmpty());
        assertTrue(violationsNull.stream().anyMatch(v -> v.getPropertyPath().toString().equals("totalAttendees")));

        RegisterAttendeesRequest zeroAttendees = new RegisterAttendeesRequest(
                "MEM_001",
                0,
                0,
                0,
                null,
                null,
                null
        );

        Set<ConstraintViolation<RegisterAttendeesRequest>> violationsZero = validator.validate(zeroAttendees);
        assertFalse(violationsZero.isEmpty());
        assertTrue(violationsZero.stream().anyMatch(v -> v.getPropertyPath().toString().equals("totalAttendees")));
    }

    @Test
    @DisplayName("Meal counts cannot be negative")
    void testNegativeMeals() {
        RegisterAttendeesRequest negativeMeals = new RegisterAttendeesRequest(
                "MEM_001",
                3,
                -1,
                -2,
                null,
                null,
                null
        );

        Set<ConstraintViolation<RegisterAttendeesRequest>> violations = validator.validate(negativeMeals);
        assertEquals(2, violations.size());
    }

    @Test
    @DisplayName("dietaryRequirements and transportationNotes cannot exceed 255 characters")
    void testFieldLengths() {
        String tooLong = "A".repeat(256);
        RegisterAttendeesRequest longNotes = new RegisterAttendeesRequest(
                "MEM_001",
                2,
                0,
                2,
                tooLong,
                tooLong,
                null
        );

        Set<ConstraintViolation<RegisterAttendeesRequest>> violations = validator.validate(longNotes);
        assertEquals(2, violations.size());
    }
}
