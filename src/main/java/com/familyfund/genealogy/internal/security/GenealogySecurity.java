package com.familyfund.genealogy.internal.security;

import com.familyfund.core.security.CurrentUserProvider;
import com.familyfund.core.security.SystemRoles;
import com.familyfund.core.tenant.TenantContext;
import com.familyfund.genealogy.internal.repository.FamilyBranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Contextual security evaluator for genealogy branches.
 * Registered as Spring bean "genealogySecurity" for evaluation in SpEL expressions.
 */
@Slf4j
@Component("genealogySecurity")
@RequiredArgsConstructor
public class GenealogySecurity {

    private final CurrentUserProvider currentUserProvider;
    private final FamilyBranchRepository familyBranchRepository;

    /**
     * Checks if caller is the designated representative (Branch Head) of the given branch.
     * System Admins and Clan Heads always have unrestricted access.
     */
    public boolean isBranchRepresentative(String branchId) {
        if (branchId == null || branchId.isBlank()) {
            return false;
        }

        if (currentUserProvider.hasRole(SystemRoles.CLAN_HEAD) || currentUserProvider.hasRole(SystemRoles.ADMIN)) {
            return true;
        }

        String clanId = TenantContext.getClanId();
        return currentUserProvider.currentMemberId()
                .map(memberId -> familyBranchRepository.findByClanIdAndBranchId(clanId, branchId.trim())
                        .map(b -> b.getRepresentative() != null && memberId.equals(b.getRepresentative().getMemberId()))
                        .orElse(false))
                .orElse(false);
    }
}
