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

@DisplayName("ReviewRegistrationRequest Bean Validation Test")
class ReviewRegistrationRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid approval request passes validation")
    void testValidApprovalRequest() {
        ReviewRegistrationRequest request = new ReviewRegistrationRequest(true, null);

        Set<ConstraintViolation<ReviewRegistrationRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Valid rejection request with reason passes validation")
    void testValidRejectionRequest() {
        ReviewRegistrationRequest request = new ReviewRegistrationRequest(false, "Đã mua đủ mâm cỗ, không thể hủy");

        Set<ConstraintViolation<ReviewRegistrationRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("approved field cannot be null")
    void testNullApproved() {
        ReviewRegistrationRequest request = new ReviewRegistrationRequest(null, "Lý do");

        Set<ConstraintViolation<ReviewRegistrationRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("approved")));
    }
}
