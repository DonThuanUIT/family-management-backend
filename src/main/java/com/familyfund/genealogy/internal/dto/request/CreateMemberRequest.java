package com.familyfund.genealogy.internal.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;

/**
 * Payload for creating a genealogy member under `POST /api/v1/branches/{branchId}/members`.
 * Every constraint mirrors table `members` (Flyway V1__init_clan_genealogy_and_events_schema.sql).
 *
 * <p>`branch_id` is NOT part of the payload: it is taken from the path variable, so the branch a
 * member belongs to is stated exactly once. `member_id`, `is_placeholder` and `created_at` are
 * server-controlled and are never accepted from clients.</p>
 */
@Builder
public record CreateMemberRequest(
        // full_name VARCHAR(100) NOT NULL
        @NotBlank(message = "Full name must not be blank")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName,

        // date_of_birth DATE (nullable)
        @PastOrPresent(message = "Date of birth cannot be in the future")
        LocalDate dateOfBirth,

        // gender VARCHAR(10) NOT NULL CHECK (gender IN ('Male', 'Female', 'Other'))
        @NotBlank(message = "Gender must not be blank")
        @Size(max = 10, message = "Gender must not exceed 10 characters")
        @Pattern(regexp = "Male|Female|Other", message = "Gender must be one of: Male, Female, Other")
        String gender,

        // generation INT NOT NULL CHECK (generation >= 1)
        @NotNull(message = "Generation must not be null")
        @Min(value = 1, message = "Generation must be greater than or equal to 1")
        Integer generation,

        // status VARCHAR(20) NOT NULL DEFAULT 'Living' CHECK (status IN ('Living', 'Deceased'))
        // Optional in the payload: null falls back to the database default 'Living'.
        @Size(max = 20, message = "Status must not exceed 20 characters")
        @Pattern(regexp = "Living|Deceased", message = "Status must be one of: Living, Deceased")
        String status,

        // death_date_lunar VARCHAR(50) (nullable)
        @Size(max = 50, message = "Lunar death date must not exceed 50 characters")
        String deathDateLunar
) {}
// branch_id VARCHAR(20) NOT NULL is validated by the controller path variable and MemberService.
// is_placeholder is not accepted from clients: MemberService always sets it to true for manager-created profiles.
