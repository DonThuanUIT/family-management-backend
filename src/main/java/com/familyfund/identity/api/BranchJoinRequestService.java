package com.familyfund.identity.api;

import com.familyfund.identity.api.dto.JoinRequestResponse;
import com.familyfund.identity.api.dto.RejectJoinRequest;
import com.familyfund.identity.api.dto.SubmitJoinRequest;
import com.familyfund.identity.internal.entity.JoinRequestApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

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

    /**
     * Lists join requests matching approval status and scoped by reviewer authorization (US-17).
     *
     * @param status         approval status filter (default: Pending)
     * @param branchId       optional branch ID filter
     * @param pageable       pagination and sorting parameters
     * @param authentication security principal
     * @return paged list of join request responses
     */
    Page<JoinRequestResponse> getJoinRequests(
            JoinRequestApprovalStatus status,
            String branchId,
            Pageable pageable,
            Authentication authentication
    );

    /**
     * Approves a branch join request, activates the user account, and creates the member on the genealogy tree (US-17).
     *
     * @param requestId      ID of the join request to approve
     * @param authentication security principal
     * @return updated join request response
     */
    JoinRequestResponse approveJoinRequest(String requestId, Authentication authentication);

    /**
     * Rejects a branch join request and deactivates the temporary user account (US-17).
     *
     * @param requestId      ID of the join request to reject
     * @param request        rejection payload containing reason
     * @param authentication security principal
     * @return updated join request response
     */
    JoinRequestResponse rejectJoinRequest(String requestId, RejectJoinRequest request, Authentication authentication);
}
