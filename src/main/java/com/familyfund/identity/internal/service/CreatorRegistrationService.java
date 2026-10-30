package com.familyfund.identity.internal.service;

import com.familyfund.identity.internal.dto.request.RegisterCreatorRequest;
import com.familyfund.identity.internal.dto.response.CreatorRegistrationResponse;

public interface CreatorRegistrationService {

    /**
     * Registers a new creator user account and bootstraps their new clan and initial family tree.
     *
     * @param request the registration payload containing creator and clan info
     * @return summary response containing created user account, clan, and member profile
     */
    CreatorRegistrationResponse registerCreator(RegisterCreatorRequest request);
}
