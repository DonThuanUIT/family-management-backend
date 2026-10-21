package com.familyfund.identity.api;

import com.familyfund.identity.api.dto.JoinRequestResponse;
import com.familyfund.identity.api.dto.SubmitJoinRequest;

/**
 * Public service contract handling branch join requests and prospective member onboarding (US-17).
 */
public interface BranchJoinRequestService {

    /**
     * Submits a self-service join request using a general clan invite code (US-17).
     * Creates an onboarding user account in 'PendingApproval' state and a join request in 'Pending' state.
     *
     * @param inviteCode the invitation code
     * @param request    the submission details
     * @return the created join request response
     */
    JoinRequestResponse submitJoinRequest(String inviteCode, SubmitJoinRequest request);
}
