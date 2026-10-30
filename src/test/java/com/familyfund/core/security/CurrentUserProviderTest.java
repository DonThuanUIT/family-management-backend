package com.familyfund.core.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    private record TestPrincipal(String getAccountId, String getMemberId) implements MemberLinkedPrincipal {}

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Object principal, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", authorities));
    }

    @Test
    @DisplayName("Reads authorities and linked member id from the security context")
    void linkedPrincipal() {
        authenticate(new TestPrincipal("ACC_1", "MEM_1"), SystemRoles.BRANCH_HEAD);
        assertTrue(provider.hasAuthority(SystemRoles.BRANCH_HEAD));
        assertFalse(provider.hasAuthority(SystemRoles.CLAN_HEAD));
        assertEquals(Optional.of("MEM_1"), provider.currentMemberId());
    }

    @Test
    @DisplayName("Plain principals and anonymous callers expose no member id or roles")
    void unlinkedOrAnonymous() {
        authenticate("plain-user", SystemRoles.ADMIN);
        assertEquals(Optional.empty(), provider.currentMemberId());

        SecurityContextHolder.clearContext();
        assertFalse(provider.hasAuthority(SystemRoles.ADMIN));
        assertEquals(Optional.empty(), provider.currentMemberId());
    }
}
