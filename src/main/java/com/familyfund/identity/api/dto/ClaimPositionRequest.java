package com.familyfund.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Payload of {@code POST /api/v1/position-links/{inviteCode}/claim} (US-19).
 *
 * <p>The pair {@code phoneNumber + password} is the identity proof of the person redeeming the code. That single
 * proof covers both cases the endpoint has to serve:</p>
 * <ul>
 *   <li>the phone is unknown - the account is created as {@code Active} and bound to the position straight away;</li>
 *   <li>the phone already belongs to an account created by the US-17 join flow
 *       ({@code account_status = 'PendingApproval'}) - the account is activated and bound to the position instead of
 *       waiting for a moderator, which is exactly what "no verification step" means here.</li>
 * </ul>
 *
 * <p>Constraints mirror {@code user_accounts} (Flyway V1/V7): {@code phone_number VARCHAR(15) NOT NULL UNIQUE},
 * {@code email VARCHAR(100) UNIQUE}, {@code password_hash VARCHAR(255)}. {@code account_id}, {@code member_id},
 * {@code account_status} and the roles are server-controlled and never accepted from clients.</p>
 */
@Builder
public record ClaimPositionRequest(
        // phone_number VARCHAR(15) NOT NULL UNIQUE - the login identifier used by POST /auth/login
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^(0|\\+84)[0-9]{9,10}$", message = "Số điện thoại không đúng định dạng (VD: 0912345678)")
        String phoneNumber,

        // password_hash VARCHAR(255) NOT NULL - stored as BCrypt, plain text never leaves this record
        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 50, message = "Mật khẩu phải từ 6 đến 50 ký tự")
        String password,

        // email VARCHAR(100) UNIQUE (nullable) - optional contact address of the new account
        @Email(message = "Email không đúng định dạng")
        @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
        String email
) {}
// full_name / date_of_birth / generation are NOT accepted: they already belong to the placeholder profile that the
// code points at, so the claimed profile is the one the clan head created in US-18, not a new one.