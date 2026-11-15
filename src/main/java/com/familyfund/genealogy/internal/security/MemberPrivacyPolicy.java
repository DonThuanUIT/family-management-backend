package com.familyfund.genealogy.internal.security;

import com.familyfund.genealogy.internal.dto.response.FieldVisibility;
import com.familyfund.genealogy.internal.dto.response.MemberFieldVisibility;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.entity.MemberPrivacySetting;
import org.springframework.stereotype.Component;

/**
 * Evaluates field-level privacy authorization for member profiles based on
 * the Hybrid Privacy Model (US-25):
 * CanView = IsSelf || OwnerConsent || ClanHead || BranchHead (managed scope) || AuthorizedOrganizer.
 */
@Component
public class MemberPrivacyPolicy {

    /**
     * Determines the visibility for each sensitive field of a member.
     *
     * @param targetMemberId ID of the member profile being inspected
     * @param targetBranchId ID of the branch the target member belongs to
     * @param setting        Personal privacy consent setting of the member (may be null)
     * @param context        Caller's evaluation context
     * @return Field-level visibility metadata
     */
    public MemberFieldVisibility evaluateVisibility(
            String targetMemberId,
            String targetBranchId,
            MemberPrivacySetting setting,
            PrivacyEvaluationContext context) {

        boolean isSelf = context.isSelf(targetMemberId);
        boolean canManage = context.canManageBranch(targetBranchId) || context.isAuthorizedOrganizer();

        boolean shareDob = setting != null && setting.isShareDateOfBirth();
        boolean sharePhone = setting != null && setting.isSharePhoneNumber();
        boolean shareEmail = setting != null && setting.isShareEmail();

        boolean canViewDob = isSelf || canManage || shareDob;
        boolean canViewPhone = isSelf || canManage || sharePhone;
        boolean canViewEmail = isSelf || canManage || shareEmail;

        return MemberFieldVisibility.builder()
                .dateOfBirth(canViewDob ? FieldVisibility.VISIBLE : FieldVisibility.RESTRICTED)
                .phoneNumber(canViewPhone ? FieldVisibility.VISIBLE : FieldVisibility.RESTRICTED)
                .email(canViewEmail ? FieldVisibility.VISIBLE : FieldVisibility.RESTRICTED)
                .build();
    }

    /**
     * Applies the privacy policy onto a {@link MemberResponse}, nullifying any restricted
     * fields and attaching the visibility metadata.
     *
     * @param rawResponse Unfiltered DTO containing raw values
     * @param setting     Personal privacy consent setting of the member (may be null)
     * @param context     Caller's evaluation context
     * @return Filtered DTO safe for API return
     */
    public MemberResponse applyPrivacy(
            MemberResponse rawResponse,
            MemberPrivacySetting setting,
            PrivacyEvaluationContext context) {

        if (rawResponse == null) {
            return null;
        }

        MemberFieldVisibility visibility = evaluateVisibility(
                rawResponse.memberId(),
                rawResponse.branchId(),
                setting,
                context
        );

        return rawResponse.toBuilder()
                .dateOfBirth(visibility.dateOfBirth() == FieldVisibility.VISIBLE ? rawResponse.dateOfBirth() : null)
                .phoneNumber(visibility.phoneNumber() == FieldVisibility.VISIBLE ? rawResponse.phoneNumber() : null)
                .email(visibility.email() == FieldVisibility.VISIBLE ? rawResponse.email() : null)
                .fieldVisibility(visibility)
                .build();
    }
}
