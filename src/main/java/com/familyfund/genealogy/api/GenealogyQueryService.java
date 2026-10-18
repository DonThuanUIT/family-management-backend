package com.familyfund.genealogy.api;

/**
 * Public query contract provided by the Genealogy module for other bounded contexts.
 */
public interface GenealogyQueryService {

    /**
     * Checks if a family branch exists in the genealogy tree.
     *
     * @param branchId ID of the branch to check
     * @return true if the branch exists, false otherwise
     */
    boolean existsBranch(String branchId);
}

