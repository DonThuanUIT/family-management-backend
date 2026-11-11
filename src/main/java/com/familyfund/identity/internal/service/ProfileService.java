package com.familyfund.identity.internal.service;

import com.familyfund.identity.internal.dto.request.ChangePasswordRequest;
import com.familyfund.identity.internal.dto.request.UpdateContactProfileRequest;
import com.familyfund.identity.internal.dto.response.UserProfileResponse;

/**
 * Service handling personal profile viewing, contact info updates, and self-service password changes (US-22).
 */
public interface ProfileService {

    UserProfileResponse getMyProfile();

    UserProfileResponse updateContactProfile(UpdateContactProfileRequest request);

    void changePassword(ChangePasswordRequest request);
}
