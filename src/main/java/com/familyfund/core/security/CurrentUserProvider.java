package com.familyfund.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads the caller's identity from the Spring Security context.
 * Exposed as a bean so services can be unit-tested with a mock.
 */
@Component
public class CurrentUserProvider {

    /**
     * @param authority full authority name, e.g. {@link SystemRoles#CLAN_HEAD}
     */
    public boolean hasAuthority(String authority) {
        Authentication authentication = currentAuthentication();
        if (authentication == null || authority == null) {
            return false;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the user_accounts.account_id linked to the caller, or empty when unauthenticated.
     */
    public Optional<String> currentAccountId() {
        Authentication authentication = currentAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof MemberLinkedPrincipal principal) {
            String accountId = principal.getAccountId();
            if (accountId != null && !accountId.isBlank()) {
                return Optional.of(accountId.trim());
            }
        }
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            return Optional.of(authentication.getName().trim());
        }
        return Optional.empty();
    }

    /**
     * @return the members.member_id linked to the caller, or empty when the principal
     *         does not implement {@link MemberLinkedPrincipal} or is not linked yet.
     */
    public Optional<String> currentMemberId() {
        Authentication authentication = currentAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof MemberLinkedPrincipal principal) {
            String memberId = principal.getMemberId();
            if (memberId != null && !memberId.isBlank()) {
                return Optional.of(memberId.trim());
            }
        }
        return Optional.empty();
    }

    private Authentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (authentication != null && authentication.isAuthenticated()) ? authentication : null;
    }
}
