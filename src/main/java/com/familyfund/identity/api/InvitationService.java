package com.familyfund.identity.api;

import com.familyfund.genealogy.api.OnboardingBranchTreeNode;
import com.familyfund.identity.api.dto.CreateGeneralInviteRequest;
import com.familyfund.identity.api.dto.InviteCodeResponse;
import com.familyfund.identity.api.dto.UpdateInvitationRequest;

import java.util.List;

/**
 * Public service contract handling clan invitation operations (US-16, US-17, US-18).
 */
public interface InvitationService {

    /**
     * Creates a general clan invitation code and shareable registration URL (US-16).
     *
     * @param request          the creation parameters
     * @param currentAccountId the ID of the authenticated user performing the creation
     * @return the created invitation code response
     */
    InviteCodeResponse createGeneralClanInvite(CreateGeneralInviteRequest request, String currentAccountId);

    /**
     * Deactivates an active invitation code, disabling future usage.
     *
     * @param inviteCode       the code to deactivate
     * @param currentAccountId the ID of the authenticated user performing deactivation
     */
    void deactivateInviteCode(String inviteCode, String currentAccountId);

    /**
     * Updates the mutable attributes of an existing invitation code: activation flag, expiry and maximum uses.
     * Only the fields present in the payload are applied; the code itself never changes.
     *
     * @param inviteCode       the code to update (must belong to the current clan)
     * @param request          the attributes to change; at least one must be present
     * @param currentAccountId the ID of the authenticated user performing the update
     * @return the updated invitation code
     */
    InviteCodeResponse updateInviteCode(String inviteCode, UpdateInvitationRequest request, String currentAccountId);

    /**
     * Issues a fresh code that replaces an existing general clan invitation: the old code is revoked
     * ({@code is_active = false}) and a new one inherits its type, target branch and usage limit, with a reset
     * {@code used_count}.
     *
     * <p>Position link codes cannot be regenerated: {@code invitation_codes.target_member_id} is UNIQUE, so a new
     * code for the same position has to be issued through the virtual-member endpoint instead.</p>
     *
     * @param inviteCode       the code to replace (must belong to the current clan)
     * @param request          optional overrides for expiry / max uses on the new code; may be {@code null}
     * @param currentAccountId the ID of the authenticated user performing the regeneration
     * @return the newly created invitation code
     */
    InviteCodeResponse regenerateInviteCode(String inviteCode, UpdateInvitationRequest request, String currentAccountId);

    /**
     * Retrieves all active general clan invitations ordered by most recent.
     *
     * @return list of active general clan invitations
     */
    List<InviteCodeResponse> getActiveGeneralInvites();

    /**
     * Retrieves details of a specific invitation code if valid and active (US-16, US-17).
     *
     * @param inviteCode the invitation code to lookup
     * @return the invitation code details
     */
    InviteCodeResponse getInviteByCode(String inviteCode);

    /**
     * Retrieves the sanitized onboarding branch tree for a specific invitation code (US-17).
     *
     * @param inviteCode the invitation code to lookup
     * @return the onboarding branch tree rooted at clan root or target branch
     */
    OnboardingBranchTreeNode getOnboardingBranchTree(String inviteCode);
}
