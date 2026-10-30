package com.familyfund.core.security;

/**
 * Spring Security authority names. Values match system_roles.role_id seeded by Flyway V2.
 */
public final class SystemRoles {

    public static final String CLAN_HEAD = "ROLE_CLAN_HEAD";
    public static final String BRANCH_HEAD = "ROLE_BRANCH_HEAD";
    public static final String MEMBER = "ROLE_MEMBER";
    public static final String ADMIN = "ROLE_ADMIN";

    private SystemRoles() {
        // Constants holder
    }
}
