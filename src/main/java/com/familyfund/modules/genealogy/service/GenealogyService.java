package com.familyfund.modules.genealogy.service;

import com.familyfund.modules.genealogy.dto.request.CreateChildBranchRequest;
import com.familyfund.modules.genealogy.dto.request.UpdateBranchRequest;
import com.familyfund.modules.genealogy.dto.request.UpdateRootBranchRequest;
import com.familyfund.modules.genealogy.dto.response.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.response.BranchTreeNodeResponse;

public interface GenealogyService {

    String DEFAULT_CLAN_ID = "CLAN_DEFAULT";

    // Multi-tenant methods (US-02 & US-04)
    BranchDetailResponse getRootBranch(String clanId);

    BranchDetailResponse updateRootBranch(String clanId, UpdateRootBranchRequest request);

    BranchDetailResponse createChildBranch(String clanId, CreateChildBranchRequest request);

    BranchDetailResponse updateBranch(String clanId, String branchId, UpdateBranchRequest request);

    BranchTreeNodeResponse getBranchTree(String clanId);

    void deleteBranch(String clanId, String branchId);

    void assignMemberToBranch(String clanId, String memberId, String branchId);

    // Default overloaded methods for backward compatibility
    default BranchDetailResponse getRootBranch() {
        return getRootBranch(DEFAULT_CLAN_ID);
    }

    default BranchDetailResponse updateRootBranch(UpdateRootBranchRequest request) {
        return updateRootBranch(DEFAULT_CLAN_ID, request);
    }

    default BranchDetailResponse createChildBranch(CreateChildBranchRequest request) {
        return createChildBranch(DEFAULT_CLAN_ID, request);
    }

    default BranchDetailResponse updateBranch(String branchId, UpdateBranchRequest request) {
        return updateBranch(DEFAULT_CLAN_ID, branchId, request);
    }

    default BranchTreeNodeResponse getBranchTree() {
        return getBranchTree(DEFAULT_CLAN_ID);
    }

    default void deleteBranch(String branchId) {
        deleteBranch(DEFAULT_CLAN_ID, branchId);
    }

    default void assignMemberToBranch(String memberId, String branchId) {
        assignMemberToBranch(DEFAULT_CLAN_ID, memberId, branchId);
    }
}
