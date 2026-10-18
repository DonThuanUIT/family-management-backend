package com.familyfund.modules.invitation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateGeneralInviteRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid request with future date and positive maxUses should pass validation")
    void validRequest_ShouldHaveNoViolations() {
        CreateGeneralInviteRequest request = CreateGeneralInviteRequest.builder()
                .targetBranchId("BRANCH_01")
                .maxUses(50)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .validityDays(7)
                .build();

        Set<ConstraintViolation<CreateGeneralInviteRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Expected no validation violations for valid request");
    }

    @Test
    @DisplayName("Request with maxUses = 0 should fail validation with @Min constraint")
    void invalidMaxUses_ShouldFailValidation() {
        CreateGeneralInviteRequest request = CreateGeneralInviteRequest.builder()
                .maxUses(0)
                .build();

        Set<ConstraintViolation<CreateGeneralInviteRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size());
        assertEquals("maxUses", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    @DisplayName("Request with expiresAt in the past should fail validation with @Future constraint")
    void pastExpiresAt_ShouldFailValidation() {
        CreateGeneralInviteRequest request = CreateGeneralInviteRequest.builder()
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        Set<ConstraintViolation<CreateGeneralInviteRequest>> violations = validator.validate(request);
        assertEquals(1, violations.size());
        assertEquals("expiresAt", violations.iterator().next().getPropertyPath().toString());
    }
}

