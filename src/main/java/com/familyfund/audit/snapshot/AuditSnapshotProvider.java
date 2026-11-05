package com.familyfund.audit.snapshot;

import com.familyfund.audit.model.AuditEntityType;

import java.util.Map;
import java.util.Optional;

/**
 * Strategy interface to take entity state snapshots for audit comparison (Diff Tracking).
 */
public interface AuditSnapshotProvider {

    /**
     * Entity type supported by this provider.
     */
    AuditEntityType supports();

    /**
     * Captures a map representation of the current entity state.
     *
     * @param entityId ID of the entity
     * @param clanId   Tenant Clan ID
     * @return Map of entity properties, or empty if entity does not exist
     */
    Optional<Map<String, Object>> snapshot(String entityId, String clanId);
}
