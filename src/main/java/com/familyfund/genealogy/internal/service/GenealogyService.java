package com.familyfund.genealogy.internal.service;

import com.familyfund.genealogy.internal.dto.request.CreateChildBranchRequest;
import com.familyfund.genealogy.internal.dto.request.UpdateBranchRequest;
import com.familyfund.genealogy.internal.dto.request.UpdateRootBranchRequest;
import com.familyfund.genealogy.internal.dto.response.BranchDetailResponse;
import com.familyfund.genealogy.internal.dto.response.BranchTreeNodeResponse;

public interface GenealogyService {

    BranchDetailResponse getRootBranch();

    BranchDetailResponse updateRootBranch(UpdateRootBranchRequest request);

    BranchDetailResponse createChildBranch(CreateChildBranchRequest request);

    BranchDetailResponse updateBranch(String branchId, UpdateBranchRequest request);

    BranchTreeNodeResponse getBranchTree();

    void deleteBranch(String branchId);

    void assignMemberToBranch(String memberId, String branchId);
}
