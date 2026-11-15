package com.familyfund.genealogy.internal.security;

import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.internal.entity.FamilyBranch;
import com.familyfund.genealogy.internal.entity.Member;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenealogySecurityTest {

    private static final String CLAN_ID = "CLAN_01";
    private static final String BRANCH_ID = "BR_01";
    private static final String MEMBER_ID = "MEM_01";

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private FamilyBranchRepository familyBranchRepository;

    @InjectMocks
    private GenealogySecurity genealogySecurity;

    @BeforeEach
    void setUp() {
        TenantContext.setClanId(CLAN_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("isBranchRepresentative - returns true for CLAN_HEAD or ADMIN")
    void isBranchRepresentative_clanHeadOrAdmin_returnsTrue() {
        when(currentUserProvider.hasRole(SystemRoles.CLAN_HEAD)).thenReturn(true);
        assertThat(genealogySecurity.isBranchRepresentative(BRANCH_ID)).isTrue();
    }

    @Test
    @DisplayName("isBranchRepresentative - returns true when member is representative of the branch")
    void isBranchRepresentative_memberIsRep_returnsTrue() {
        when(currentUserProvider.hasRole(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.hasRole(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of(MEMBER_ID));

        Member rep = Member.builder().memberId(MEMBER_ID).build();
        FamilyBranch branch = FamilyBranch.builder().branchId(BRANCH_ID).representative(rep).build();

        when(familyBranchRepository.findByClanIdAndBranchId(CLAN_ID, BRANCH_ID)).thenReturn(Optional.of(branch));

        assertThat(genealogySecurity.isBranchRepresentative(BRANCH_ID)).isTrue();
    }

    @Test
    @DisplayName("isBranchRepresentative - returns false when member is NOT representative")
    void isBranchRepresentative_memberNotRep_returnsFalse() {
        when(currentUserProvider.hasRole(SystemRoles.CLAN_HEAD)).thenReturn(false);
        when(currentUserProvider.hasRole(SystemRoles.ADMIN)).thenReturn(false);
        when(currentUserProvider.currentMemberId()).thenReturn(Optional.of("MEM_OTHER"));

        Member rep = Member.builder().memberId(MEMBER_ID).build();
        FamilyBranch branch = FamilyBranch.builder().branchId(BRANCH_ID).representative(rep).build();

        when(familyBranchRepository.findByClanIdAndBranchId(CLAN_ID, BRANCH_ID)).thenReturn(Optional.of(branch));

        assertThat(genealogySecurity.isBranchRepresentative(BRANCH_ID)).isFalse();
    }

    @Test
    @DisplayName("isBranchRepresentative - returns false for blank branchId")
    void isBranchRepresentative_blankInput_returnsFalse() {
        assertThat(genealogySecurity.isBranchRepresentative(null)).isFalse();
        assertThat(genealogySecurity.isBranchRepresentative("  ")).isFalse();
    }
}
