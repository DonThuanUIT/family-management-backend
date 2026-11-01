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

@DisplayName("RequestCancelRegistrationRequest Bean Validation Test")
class RequestCancelRegistrationRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid cancellation request passes validation")
    void testValidCancellationRequest() {
        RequestCancelRegistrationRequest request = new RequestCancelRegistrationRequest(
                "Cả nhà có tang đột xuất, không thể tham dự ngày lễ giỗ"
        );

        Set<ConstraintViolation<RequestCancelRegistrationRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("cancellationReason cannot be blank")
    void testBlankCancellationReason() {
        RequestCancelRegistrationRequest request = new RequestCancelRegistrationRequest("");

        Set<ConstraintViolation<RequestCancelRegistrationRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("cancellationReason")));
    }
}
