package com.familyfund.genealogy.api;

/**
 * Public query contract provided by the Genealogy module for other bounded contexts.
 */
public interface GenealogyQueryService {

    /**
     * Checks if a family branch exists in the genealogy tree within the current tenant context.
     *
     * @param branchId ID of the branch to check
     * @return true if the branch exists in the current tenant clan, false otherwise
     * @deprecated Prefer {@link #existsBranch(String, String)} to enforce explicit multi-tenant isolation.
     */
    @Deprecated
    boolean existsBranch(String branchId);

    /**
     * Checks if a family branch exists in the genealogy tree for a specific clan.
     *
     * @param clanId   ID of the clan to check within
     * @param branchId ID of the branch to check
     * @return true if the branch exists in the specified clan, false otherwise
     */
    boolean existsBranch(String clanId, String branchId);
}
