package com.familyfund.identity.internal.service;

import com.familyfund.core.exception.BadRequestException;
import com.familyfund.core.exception.InviteCodeException;
import com.familyfund.core.exception.ResourceNotFoundException;
import com.familyfund.identity.internal.entity.InvitationCode;
import com.familyfund.identity.internal.entity.InviteType;
import com.familyfund.identity.internal.repository.InvitationCodeRepository;

import java.time.Instant;

/**
 * Single gate every invitation code goes through before it is accepted (US-16, US-17, US-18, US-19).
 *
 * <p>Keeping the rules here means the public onboarding endpoints and the position-link endpoints cannot drift
 * apart, and every rejection carries an {@link InviteCodeException} with a machine-readable reason so callers can
 * distinguish <em>revoked</em>, <em>expired</em> and <em>exhausted</em> codes instead of guessing from the text.</p>
 *
 * <p>Deliberately a stateless helper: it needs the repository only to read, and each service already holds that
 * repository, so no extra collaborator has to be wired through the module.</p>
 */
final class InviteCodeGuard {

    /** invitation_codes.invite_code is VARCHAR(30). */
    static final int MAX_CODE_LENGTH = 30;

    static final String ONBOARDING_LABEL = "mời";
    static final String POSITION_LINK_LABEL = "liên kết vị trí";

    private InviteCodeGuard() {
    }

    /**
     * Validates a general clan invite code used for onboarding (preview, branch tree, join submission).
     *
     * @throws ResourceNotFoundException when the code does not exist at all (404)
     * @throws InviteCodeException       when the code is revoked, expired or exhausted (400 + code)
     */
    static InvitationCode requireUsableForOnboarding(InvitationCodeRepository repository, String rawCode) {
        String code = normalize(rawCode, ONBOARDING_LABEL);

        InvitationCode active = repository.findByInviteCodeAndIsActiveTrue(code).orElse(null);
        if (active == null) {
            // A row that exists but is inactive is *revoked*, not missing: say so instead of a generic 404.
            if (repository.findByInviteCode(code).isPresent()) {
                throw InviteCodeException.revoked(ONBOARDING_LABEL, code);
            }
            throw new ResourceNotFoundException("Mã mời", "inviteCode", code);
        }

        requireWithinValidity(active, ONBOARDING_LABEL);
        return active;
    }

    /**
     * Validates a one-time position link code used to preview / claim a virtual genealogy position (US-19).
     *
     * @throws ResourceNotFoundException when the code does not exist or is not a {@code PositionLinkCode} (404)
     * @throws InviteCodeException       when the code is revoked, expired or exhausted (400 + code)
     */
    static InvitationCode requireUsableForPositionLink(InvitationCodeRepository repository, String rawCode) {
        String code = normalize(rawCode, POSITION_LINK_LABEL);

        // A general clan invite is not a position link, so it stays invisible here (404).
        InvitationCode invitation = repository.findById(code)
                .filter(entity -> entity.getInviteType() == InviteType.PositionLinkCode)
                .orElseThrow(() -> new ResourceNotFoundException("Mã liên kết vị trí", "inviteCode", code));

        if (!Boolean.TRUE.equals(invitation.getIsActive())) {
            throw InviteCodeException.revoked(POSITION_LINK_LABEL, code);
        }
        requireWithinValidity(invitation, POSITION_LINK_LABEL);
        if (invitation.getTargetMemberId() == null || invitation.getTargetMemberId().isBlank()) {
            throw new BadRequestException("Mã liên kết vị trí không gắn với vị trí phả hệ nào");
        }
        return invitation;
    }

    private static void requireWithinValidity(InvitationCode invitation, String label) {
        if (invitation.getExpiresAt() != null && !invitation.getExpiresAt().isAfter(Instant.now())) {
            throw InviteCodeException.expired(label, invitation.getInviteCode());
        }
        if (invitation.getMaxUses() != null && invitation.getUsedCount() != null
                && invitation.getUsedCount() >= invitation.getMaxUses()) {
            throw InviteCodeException.exhausted(label, invitation.getInviteCode());
        }
    }

    private static String normalize(String rawCode, String label) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BadRequestException("Mã " + label + " không được để trống");
        }
        String code = rawCode.trim();
        if (code.length() > MAX_CODE_LENGTH) {
            throw new BadRequestException("Mã " + label + " không được vượt quá " + MAX_CODE_LENGTH + " ký tự");
        }
        return code;
    }
}