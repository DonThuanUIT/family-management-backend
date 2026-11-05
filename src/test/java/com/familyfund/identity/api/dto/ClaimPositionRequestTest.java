package com.familyfund.identity.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link ClaimPositionRequest} only carries what a registration needs (US-19) and that its constraints
 * mirror table {@code user_accounts}: {@code phone_number VARCHAR(15) NOT NULL UNIQUE}
 * ({@code ^(0|\+84)[0-9]{9,10}$}), {@code email VARCHAR(100) UNIQUE} and a password that BCrypt can hash.
 *
 * <p>The profile fields ({@code fullName}, {@code dateOfBirth}, {@code gender}, {@code generation}) must NOT be part
 * of the payload: registering with a position link code joins an existing placeholder profile, it does not create or
 * rewrite one.</p>
 */
class ClaimPositionRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private ClaimPositionRequest.ClaimPositionRequestBuilder valid() {
        return ClaimPositionRequest.builder()
                .phoneNumber("0912345678")
                .password("secret123")
                .email("an@example.com");
    }

    private Set<String> violatedFields(ClaimPositionRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("A complete registration passes, and the email is optional")
    void validRequest_ShouldPass() {
        assertTrue(validator.validate(valid().build()).isEmpty());
        assertTrue(validator.validate(valid().email(null).build()).isEmpty());
    }

    @Test
    @DisplayName("Phone number and password are required")
    void missingRequiredFields_ShouldFail() {
        assertEquals(Set.of("phoneNumber", "password"),
                violatedFields(ClaimPositionRequest.builder().build()));
    }

    @Test
    @DisplayName("Phone numbers outside the Vietnamese format are rejected")
    void invalidPhoneNumbers_ShouldFail() {
        assertEquals(Set.of("phoneNumber"), violatedFields(valid().phoneNumber("12345").build()));
        assertEquals(Set.of("phoneNumber"), violatedFields(valid().phoneNumber("0912345678901").build()));
        assertEquals(Set.of("phoneNumber"), violatedFields(valid().phoneNumber("+1 555 0100").build()));
        assertTrue(validator.validate(valid().phoneNumber("+84912345678").build()).isEmpty());
    }

    @Test
    @DisplayName("Passwords must stay within 6..50 characters")
    void invalidPasswordLength_ShouldFail() {
        assertEquals(Set.of("password"), violatedFields(valid().password("12345").build()));
        assertEquals(Set.of("password"), violatedFields(valid().password("P".repeat(51)).build()));
        assertEquals(Set.of("password"), violatedFields(valid().password("   ").build()));
    }

    @Test
    @DisplayName("A malformed or oversized email is rejected")
    void invalidEmail_ShouldFail() {
        assertEquals(Set.of("email"), violatedFields(valid().email("not-an-email").build()));
        assertEquals(Set.of("email"),
                violatedFields(valid().email("a".repeat(95) + "@example.com").build()));
    }

    @Test
    @DisplayName("Profile and server-controlled columns are not accepted in the body")
    void serverControlledFields_AreNotPartOfPayload() {
        Set<String> components = Arrays.stream(ClaimPositionRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toSet());

        // The position already exists (US-18): the claimant never re-declares who they are in the tree.
        assertFalse(components.contains("fullName"));
        assertFalse(components.contains("dateOfBirth"));
        assertFalse(components.contains("gender"));
        assertFalse(components.contains("generation"));
        assertFalse(components.contains("branchId"));
        assertFalse(components.contains("memberId"));

        // Server-controlled account columns.
        assertFalse(components.contains("accountId"));
        assertFalse(components.contains("memberId"));
        assertFalse(components.contains("accountStatus"));
        assertFalse(components.contains("roles"));
        assertFalse(components.contains("inviteCode"));
    }
}