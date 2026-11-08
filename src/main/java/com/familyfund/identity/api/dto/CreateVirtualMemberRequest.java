package com.familyfund.identity.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Payload for {@code POST /api/v1/branches/{branchId}/virtual-members} (US-18).
 *
 * <p>Every constraint mirrors table {@code members} (Flyway V1__init_clan_genealogy_and_events_schema.sql), the
 * same way {@code CreateMemberRequest} does for US-03. The branch is a path variable, so it is absent here, and the
 * server-controlled columns {@code member_id}, {@code is_placeholder} and {@code created_at} are never accepted
 * from clients.</p>
 *
 * <p>The last two components configure the one-time {@code PositionLinkCode} that is issued with the profile:</p>
 * <ul>
 *   <li>{@code expiresAt} - absolute expiry of the code ({@code invitation_codes.expires_at});</li>
 *   <li>{@code validityDays} - relative validity, used only when {@code expiresAt} is absent. Defaults to 7 days.</li>
 * </ul>
 */
@Builder
public record CreateVirtualMemberRequest(
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
        // Optional: null falls back to the database default 'Living'.
        @Size(max = 20, message = "Status must not exceed 20 characters")
        @Pattern(regexp = "Living|Deceased", message = "Status must be one of: Living, Deceased")
        String status,

        // death_date_lunar VARCHAR(50) (nullable)
        @Size(max = 50, message = "Lunar death date must not exceed 50 characters")
        String deathDateLunar,

        // invitation_codes.expires_at TIMESTAMPTZ (nullable -> validityDays -> default 7 days)
        @Future(message = "Link expiry must be in the future")
        Instant expiresAt,

        // Relative validity of the position link code, in days
        @Min(value = 1, message = "Validity must be at least 1 day")
        @Max(value = 365, message = "Validity must not exceed 365 days")
        Integer validityDays
) {}
// branch_id VARCHAR(20) NOT NULL comes from the request path and is validated by PositionLinkService.
// is_placeholder is not accepted: the genealogy module always persists a virtual profile as a placeholder.
// max_uses / used_count are server-forced to 1 / 0 because a PositionLinkCode is one-time use.
