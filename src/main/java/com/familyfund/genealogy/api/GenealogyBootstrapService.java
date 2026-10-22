package com.familyfund.genealogy.api;

import com.familyfund.genealogy.api.dto.ClanBootstrapCommand;
import com.familyfund.genealogy.api.dto.ClanBootstrapResult;

/**
 * Public service contract to bootstrap a new clan lineage and initial tree structure.
 * Exposed to `identity` module per Spring Modulith modular architecture rules.
 */
public interface GenealogyBootstrapService {

    /**
     * Bootstraps a new clan, root family branch, optional ancestor placeholder, and creator member.
     *
     * @param command data payload containing clan metadata and creator member details
     * @return result containing generated identifiers and initialized entities
     */
    ClanBootstrapResult bootstrapClanAndCreator(ClanBootstrapCommand command);
}
