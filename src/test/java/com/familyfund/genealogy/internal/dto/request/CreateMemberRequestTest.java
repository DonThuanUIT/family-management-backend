package com.familyfund.genealogy.internal.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that CreateMemberRequest constraints mirror table `members` (Flyway V1).
 * `branch_id` is intentionally absent from the payload: it travels in the request path
 * (POST /api/v1/branches/{branchId}/members) and is validated by MemberServiceImpl.
 */
class CreateMemberRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private CreateMemberRequest.CreateMemberRequestBuilder valid() {
        return CreateMemberRequest.builder()
                .fullName("Nguyen Van An")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("Male")
                .generation(3)
                .status("Living");
    }

    private Set<String> violatedFields(CreateMemberRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Valid request passes; optional status/dateOfBirth may be null")
    void validRequest_ShouldPass() {
        assertTrue(validator.validate(valid().build()).isEmpty());
        assertTrue(validator.validate(valid().status(null).dateOfBirth(null).build()).isEmpty());
    }

    @Test
    @DisplayName("NOT NULL columns are enforced: full_name, gender, generation")
    void missingRequiredFields_ShouldFail() {
        CreateMemberRequest request = CreateMemberRequest.builder().build();
        assertEquals(Set.of("fullName", "gender", "generation"), violatedFields(request));
    }

    @Test
    @DisplayName("VARCHAR length limits are enforced: full_name(100), death_date_lunar(50)")
    void tooLongFields_ShouldFail() {
        CreateMemberRequest request = valid()
                .fullName("A".repeat(101))
                .deathDateLunar("B".repeat(51))
                .build();
        assertEquals(Set.of("fullName", "deathDateLunar"), violatedFields(request));
    }

    @Test
    @DisplayName("CHECK constraints are enforced: gender, status, generation >= 1")
    void checkConstraints_ShouldFail() {
        CreateMemberRequest request = valid()
                .gender("male")
                .status("Alive")
                .generation(0)
                .build();
        assertEquals(Set.of("gender", "status", "generation"), violatedFields(request));
    }

    @Test
    @DisplayName("Date of birth cannot be in the future")
    void futureDateOfBirth_ShouldFail() {
        CreateMemberRequest request = valid().dateOfBirth(LocalDate.now().plusDays(1)).build();
        assertEquals(Set.of("dateOfBirth"), violatedFields(request));
    }

    @Test
    @DisplayName("branchId is not accepted in the body: the branch comes from the request path")
    void branchId_IsNotPartOfPayload() {
        Set<String> components = Arrays.stream(CreateMemberRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toSet());
        assertFalse(components.contains("branchId"));
        assertFalse(components.contains("memberId"));
        assertFalse(components.contains("isPlaceholder"));
    }
}
