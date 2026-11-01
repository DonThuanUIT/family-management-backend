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

@DisplayName("RequestModifyRegistrationRequest Bean Validation Test")
class RequestModifyRegistrationRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid modification request passes validation")
    void testValidModificationRequest() {
        RequestModifyRegistrationRequest request = new RequestModifyRegistrationRequest(
                3,
                1,
                2,
                "Chay 1 suất",
                "Xe máy",
                "Gia đình có 2 người bận thi cử, xin giảm cỗ"
        );

        Set<ConstraintViolation<RequestModifyRegistrationRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("proposedTotalAttendees cannot be null or zero")
    void testProposedTotalAttendeesValidation() {
        RequestModifyRegistrationRequest request = new RequestModifyRegistrationRequest(
                0,
                0,
                0,
                null,
                null,
                "Lý do thay đổi hợp lệ"
        );

        Set<ConstraintViolation<RequestModifyRegistrationRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("proposedTotalAttendees")));
    }

    @Test
    @DisplayName("modificationReason is mandatory and cannot be blank")
    void testModificationReasonBlank() {
        RequestModifyRegistrationRequest request = new RequestModifyRegistrationRequest(
                2,
                0,
                2,
                null,
                null,
                "   "
        );

        Set<ConstraintViolation<RequestModifyRegistrationRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("modificationReason")));
    }
}
