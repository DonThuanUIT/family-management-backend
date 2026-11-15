package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.internal.dto.request.UpdateMemberPrivacySettingRequest;
import com.familyfund.genealogy.internal.dto.response.MemberPrivacySettingResponse;

/**
 * Service managing member personal privacy consent settings (US-25).
 */
public interface MemberPrivacySettingService {

    /**
     * Retrieves the privacy consent settings of the currently authenticated member.
     */
    MemberPrivacySettingResponse getMyPrivacySettings();

    /**
     * Updates the privacy consent settings of the currently authenticated member.
     */
    MemberPrivacySettingResponse updateMyPrivacySettings(UpdateMemberPrivacySettingRequest request);
}
