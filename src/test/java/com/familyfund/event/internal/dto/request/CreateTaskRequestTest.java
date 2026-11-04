package com.familyfund.event.internal.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreateTaskRequest Bean Validation Test")
class CreateTaskRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid request should have no validation violations")
    void testValidRequest() {
        CreateTaskRequest request = new CreateTaskRequest(
                "Mua sắm lễ vật cúng tổ",
                "5 mâm ngũ quả, hoa tươi",
                BigDecimal.valueOf(2000000),
                Instant.now().plus(5, ChronoUnit.DAYS),
                "BR_CHI_2",
                "MEM_MAI_ANH"
        );

        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should produce no violations");
        assertEquals(BigDecimal.valueOf(2000000), request.advanceBudget());
    }

    @Test
    @DisplayName("Default advanceBudget is ZERO when omitted or passed as null")
    void testDefaultAdvanceBudget() {
        CreateTaskRequest request = new CreateTaskRequest(
                "Khánh tiết từ đường",
                null,
                null,
                Instant.now().plus(3, ChronoUnit.DAYS),
                null,
                null
        );

        assertEquals(BigDecimal.ZERO, request.advanceBudget());
        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Blank task name should fail validation")
    void testBlankTaskName() {
        CreateTaskRequest request = new CreateTaskRequest(
                "   ",
                "Nội dung",
                BigDecimal.ZERO,
                Instant.now().plus(2, ChronoUnit.DAYS),
                null,
                null
        );

        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Tên công việc không được để trống")));
    }

    @Test
    @DisplayName("Task name longer than 100 characters should fail validation")
    void testTaskNameTooLong() {
        String longName = "A".repeat(101);
        CreateTaskRequest request = new CreateTaskRequest(
                longName,
                "Chi tiết",
                BigDecimal.ZERO,
                Instant.now().plus(2, ChronoUnit.DAYS),
                null,
                null
        );

        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Tên công việc tối đa 100 ký tự")));
    }

    @Test
    @DisplayName("Negative advanceBudget should fail validation")
    void testNegativeAdvanceBudget() {
        CreateTaskRequest request = new CreateTaskRequest(
                "Chuẩn bị bàn ghế",
                "Chi tiết",
                BigDecimal.valueOf(-1000),
                Instant.now().plus(2, ChronoUnit.DAYS),
                null,
                null
        );

        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Ngân sách tạm ứng dự kiến phải lớn hơn hoặc bằng 0")));
    }

    @Test
    @DisplayName("Null deadline should fail validation")
    void testNullDeadline() {
        CreateTaskRequest request = new CreateTaskRequest(
                "Chuẩn bị mâm cỗ",
                "Chi tiết",
                BigDecimal.ZERO,
                null,
                null,
                null
        );

        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Hạn chót công việc không được để trống")));
    }

    @Test
    @DisplayName("Past deadline should fail validation")
    void testPastDeadline() {
        CreateTaskRequest request = new CreateTaskRequest(
                "Thuê phông rạp",
                "Chi tiết",
                BigDecimal.ZERO,
                Instant.now().minus(1, ChronoUnit.DAYS),
                null,
                null
        );

        Set<ConstraintViolation<CreateTaskRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Hạn chót công việc phải là thời điểm trong tương lai")));
    }
}

