package com.familyfund.contribution.internal.dto.response;

/**
 * Result returned after performing an asset inventory audit (US-52).
 */
public record AuditClanAssetResultResponse(
        ClanAssetResponse asset,
        ClanAssetAuditResponse auditLog,
        String message
) {
}
