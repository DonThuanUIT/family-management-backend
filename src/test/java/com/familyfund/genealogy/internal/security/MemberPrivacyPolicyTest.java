package com.familyfund.genealogy.internal.security;

import com.familyfund.genealogy.internal.dto.response.FieldVisibility;
import com.familyfund.genealogy.internal.dto.response.MemberFieldVisibility;
import com.familyfund.genealogy.internal.dto.response.MemberResponse;
import com.familyfund.genealogy.internal.entity.MemberPrivacySetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("US-25: MemberPrivacyPolicy Unit Tests (Hybrid Privacy)")
class MemberPrivacyPolicyTest {

    private MemberPrivacyPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new MemberPrivacyPolicy();
    }

    @Test
    @DisplayName("Self-view: Member viewing their own profile sees all sensitive fields")
    void shouldAllowFullAccessWhenCallerIsSelf() {
        PrivacyEvaluationContext ctx = PrivacyEvaluationContext.builder()
                .callerMemberId("MEM_SELF")
                .isClanHead(false)
                .managedBranchIds(Set.of())
                .build();

        MemberPrivacySetting setting = MemberPrivacySetting.builder()
                .memberId("MEM_SELF")
                .shareDateOfBirth(false)
                .sharePhoneNumber(false)
                .shareEmail(false)
                .build();

        MemberFieldVisibility visibility = policy.evaluateVisibility("MEM_SELF", "BR_1", setting, ctx);

        assertEquals(FieldVisibility.VISIBLE, visibility.dateOfBirth());
        assertEquals(FieldVisibility.VISIBLE, visibility.phoneNumber());
        assertEquals(FieldVisibility.VISIBLE, visibility.email());
    }

    @Test
    @DisplayName("Clan Head: Has full visibility across all clan members regardless of consent settings")
    void shouldAllowFullAccessWhenCallerIsClanHead() {
        PrivacyEvaluationContext ctx = PrivacyEvaluationContext.builder()
                .callerMemberId("MEM_CLAN_HEAD")
                .isClanHead(true)
                .managedBranchIds(Set.of())
                .build();

        MemberPrivacySetting setting = MemberPrivacySetting.builder()
                .memberId("MEM_TARGET")
                .shareDateOfBirth(false)
                .sharePhoneNumber(false)
                .shareEmail(false)
                .build();

        MemberFieldVisibility visibility = policy.evaluateVisibility("MEM_TARGET", "BR_ANY", setting, ctx);

        assertEquals(FieldVisibility.VISIBLE, visibility.dateOfBirth());
        assertEquals(FieldVisibility.VISIBLE, visibility.phoneNumber());
        assertEquals(FieldVisibility.VISIBLE, visibility.email());
    }

    @Test
    @DisplayName("Branch Head: Has full visibility for members in managed branch and child branches")
    void shouldAllowFullAccessWhenCallerIsBranchHeadOfManagedBranch() {
        PrivacyEvaluationContext ctx = PrivacyEvaluationContext.builder()
                .callerMemberId("MEM_BRANCH_HEAD")
                .isClanHead(false)
                .managedBranchIds(Set.of("BR_ROOT", "BR_CHILD_1"))
                .build();

        MemberPrivacySetting setting = MemberPrivacySetting.builder()
                .memberId("MEM_TARGET")
                .shareDateOfBirth(false)
                .sharePhoneNumber(false)
                .shareEmail(false)
                .build();

        MemberFieldVisibility visibility = policy.evaluateVisibility("MEM_TARGET", "BR_CHILD_1", setting, ctx);

        assertEquals(FieldVisibility.VISIBLE, visibility.dateOfBirth());
        assertEquals(FieldVisibility.VISIBLE, visibility.phoneNumber());
        assertEquals(FieldVisibility.VISIBLE, visibility.email());
    }

    @Test
    @DisplayName("Branch Head: Restricted when viewing members outside their managed branches without consent")
    void shouldRestrictWhenCallerIsBranchHeadOfUnrelatedBranch() {
        PrivacyEvaluationContext ctx = PrivacyEvaluationContext.builder()
                .callerMemberId("MEM_BRANCH_HEAD")
                .isClanHead(false)
                .managedBranchIds(Set.of("BR_CHILD_1"))
                .build();

        MemberPrivacySetting setting = MemberPrivacySetting.builder()
                .memberId("MEM_TARGET")
                .shareDateOfBirth(false)
                .sharePhoneNumber(false)
                .shareEmail(false)
                .build();

        MemberFieldVisibility visibility = policy.evaluateVisibility("MEM_TARGET", "BR_CHILD_2", setting, ctx);

        assertEquals(FieldVisibility.RESTRICTED, visibility.dateOfBirth());
        assertEquals(FieldVisibility.RESTRICTED, visibility.phoneNumber());
        assertEquals(FieldVisibility.RESTRICTED, visibility.email());
    }

    @Test
    @DisplayName("Owner Consent: Regular member sees fields that the owner has explicitly shared")
    void shouldRespectOwnerConsentForRegularMember() {
        PrivacyEvaluationContext ctx = PrivacyEvaluationContext.builder()
                .callerMemberId("MEM_OTHER")
                .isClanHead(false)
                .managedBranchIds(Set.of())
                .build();

        MemberPrivacySetting setting = MemberPrivacySetting.builder()
                .memberId("MEM_TARGET")
                .shareDateOfBirth(true)
                .sharePhoneNumber(false)
                .shareEmail(true)
                .build();

        MemberFieldVisibility visibility = policy.evaluateVisibility("MEM_TARGET", "BR_1", setting, ctx);

        assertEquals(FieldVisibility.VISIBLE, visibility.dateOfBirth(), "Owner consented to share dateOfBirth");
        assertEquals(FieldVisibility.RESTRICTED, visibility.phoneNumber(), "Owner did not consent to share phoneNumber");
        assertEquals(FieldVisibility.VISIBLE, visibility.email(), "Owner consented to share email");
    }

    @Test
    @DisplayName("Privacy by Default: Restrict all when setting is null (e.g. placeholder profile)")
    void shouldRestrictAllWhenSettingIsNullAndCallerIsRegularMember() {
        PrivacyEvaluationContext ctx = PrivacyEvaluationContext.builder()
                .callerMemberId("MEM_REGULAR")
                .isClanHead(false)
                .managedBranchIds(Set.of())
                .build();

        MemberFieldVisibility visibility = policy.evaluateVisibility("MEM_PLACEHOLDER", "BR_1", null, ctx);

        assertEquals(FieldVisibility.RESTRICTED, visibility.dateOfBirth());
        assertEquals(FieldVisibility.RESTRICTED, visibility.phoneNumber());
        assertEquals(FieldVisibility.RESTRICTED, visibility.email());
    }

    @Test
    @DisplayName("applyPrivacy: Correctly nullifies restricted fields and attaches metadata on MemberResponse")
    void shouldApplyPrivacyAndNullifyRestrictedFields() {
        PrivacyEvaluationContext ctx = PrivacyEvaluationContext.builder()
                .callerMemberId("MEM_REGULAR")
                .isClanHead(false)
                .managedBranchIds(Set.of())
                .build();

        MemberResponse raw = MemberResponse.builder()
                .memberId("MEM_TARGET")
                .fullName("Nguyen Van B")
                .dateOfBirth(LocalDate.of(1995, 8, 20))
                .phoneNumber("0912345678")
                .email("b.nguyen@example.com")
                .branchId("BR_1")
                .build();

        MemberPrivacySetting setting = MemberPrivacySetting.builder()
                .memberId("MEM_TARGET")
                .shareDateOfBirth(true)
                .sharePhoneNumber(false)
                .shareEmail(false)
                .build();

        MemberResponse filtered = policy.applyPrivacy(raw, setting, ctx);

        assertEquals(LocalDate.of(1995, 8, 20), filtered.dateOfBirth(), "dateOfBirth was shared and should remain");
        assertNull(filtered.phoneNumber(), "phoneNumber was not shared and must be nullified");
        assertNull(filtered.email(), "email was not shared and must be nullified");
        assertEquals(FieldVisibility.VISIBLE, filtered.fieldVisibility().dateOfBirth());
        assertEquals(FieldVisibility.RESTRICTED, filtered.fieldVisibility().phoneNumber());
        assertEquals(FieldVisibility.RESTRICTED, filtered.fieldVisibility().email());
    }
}
