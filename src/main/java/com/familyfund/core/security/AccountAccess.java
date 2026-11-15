package com.familyfund.core.security;

import java.util.Set;

/** Live identity port: implementation stays in identity, avoiding a genealogy -> identity cycle. */
public interface AccountAccess {
    Snapshot current();

    record Snapshot(String accountId, String memberId, String clanId, Set<String> roles) {
        public Snapshot { roles = Set.copyOf(roles); }
        public boolean hasRole(String role) { return roles.contains(role); }
    }
}
