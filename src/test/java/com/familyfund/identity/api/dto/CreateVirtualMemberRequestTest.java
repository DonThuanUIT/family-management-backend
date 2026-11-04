package com.familyfund.identity.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that CreateVirtualMemberRequest constraints mirror table `members` (Flyway V1) and the expiry rules of
 * the one-time `PositionLinkCode` (US-18). `branch_id` is intentionally absent from the payload: it travels in the
 * request path (POST /api/v1/branches/{branchId}/virtual-members) and is validated by PositionLinkServiceImpl.
 */
class CreateVirtualMemberRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private CreateVirtualMemberRequest.CreateVirtualMemberRequestBuilder valid() {
        return CreateVirtualMemberRequest.builder()
                .fullName("Nguyen Van An")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("Male")
                .generation(3)
                .status("Living");
    }

    private Set<String> violatedFields(CreateVirtualMemberRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Valid request passes; optional profile fields and link expiry may be null")
    void validRequest_ShouldPass() {
        assertTrue(validator.validate(valid().build()).isEmpty());
        assertTrue(validator.validate(valid().status(null).dateOfBirth(null).deathDateLunar(null).build()).isEmpty());
        assertTrue(validator.validate(valid().expiresAt(Instant.now().plus(3, ChronoUnit.DAYS)).build()).isEmpty());
        assertTrue(validator.validate(valid().validityDays(30).build()).isEmpty());
    }

    @Test
    @DisplayName("NOT NULL columns are enforced: full_name, gender, generation")
    void missingRequiredFields_ShouldFail() {
        assertEquals(Set.of("fullName", "gender", "generation"),
                violatedFields(CreateVirtualMemberRequest.builder().build()));
    }

    @Test
    @DisplayName("VARCHAR length limits are enforced: full_name(100), death_date_lunar(50)")
    void tooLongFields_ShouldFail() {
        CreateVirtualMemberRequest request = valid()
                .fullName("A".repeat(101))
                .deathDateLunar("B".repeat(51))
                .build();
        assertEquals(Set.of("fullName", "deathDateLunar"), violatedFields(request));
    }

    @Test
    @DisplayName("CHECK constraints are enforced: gender, status, generation >= 1")
    void checkConstraints_ShouldFail() {
        CreateVirtualMemberRequest request = valid()
                .gender("male")
                .status("Alive")
                .generation(0)
                .build();
        assertEquals(Set.of("gender", "status", "generation"), violatedFields(request));
    }

    @Test
    @DisplayName("Date of birth cannot be in the future")
    void futureDateOfBirth_ShouldFail() {
        CreateVirtualMemberRequest request = valid().dateOfBirth(LocalDate.now().plusDays(1)).build();
        assertEquals(Set.of("dateOfBirth"), violatedFields(request));
    }

    @Test
    @DisplayName("Link expiry must be in the future and validity must stay within 1..365 days")
    void invalidExpiry_ShouldFail() {
        CreateVirtualMemberRequest pastExpiry = valid().expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES)).build();
        assertEquals(Set.of("expiresAt"), violatedFields(pastExpiry));

        assertEquals(Set.of("validityDays"), violatedFields(valid().validityDays(0).build()));
        assertEquals(Set.of("validityDays"), violatedFields(valid().validityDays(366).build()));
    }

    @Test
    @DisplayName("Server-controlled columns are not accepted in the body")
    void serverControlledFields_AreNotPartOfPayload() {
        Set<String> components = Arrays.stream(CreateVirtualMemberRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toSet());

        assertFalse(components.contains("branchId"));
        assertFalse(components.contains("memberId"));
        assertFalse(components.contains("isPlaceholder"));
        assertFalse(components.contains("maxUses"));
        assertFalse(components.contains("usedCount"));
        assertFalse(components.contains("inviteCode"));
    }
}
