package com.familyfund.modules.invitation.service;

import com.familyfund.modules.invitation.dto.CreateGeneralInviteRequest;
import com.familyfund.modules.invitation.dto.InviteCodeResponse;

import java.util.List;

/**
 * Service handling clan invitation operations (US-16, US-18).
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
     * Retrieves all active general clan invitations ordered by most recent.
     *
     * @return list of active general clan invitations
     */
    List<InviteCodeResponse> getActiveGeneralInvites();

    /**
     * Retrieves details of a specific invitation code if valid and active.
     *
     * @param inviteCode the invitation code to lookup
     * @return the invitation code details
     */
    InviteCodeResponse getInviteByCode(String inviteCode);
}

