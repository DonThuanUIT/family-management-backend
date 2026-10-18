package com.familyfund.core.tenant;

/**
 * ThreadLocal context holder for the current tenant (Clan ID).
 * Falls back to DEFAULT_CLAN_ID when no tenant has been explicitly set.
 */
public final class TenantContext {

    public static final String DEFAULT_CLAN_ID = "CLAN_DEFAULT";
    private static final ThreadLocal<String> CURRENT_CLAN = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    public static void setClanId(String clanId) {
        if (clanId != null && !clanId.isBlank()) {
            CURRENT_CLAN.set(clanId.trim());
        } else {
            CURRENT_CLAN.remove();
        }
    }

    public static String getClanId() {
        String clanId = CURRENT_CLAN.get();
        return (clanId != null && !clanId.isBlank()) ? clanId : DEFAULT_CLAN_ID;
    }

    public static void clear() {
        CURRENT_CLAN.remove();
    }
}
