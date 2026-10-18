package com.familyfund.modules.genealogy.service;

import com.familyfund.modules.genealogy.dto.request.CreateChildBranchRequest;
import com.familyfund.modules.genealogy.dto.request.UpdateBranchRequest;
import com.familyfund.modules.genealogy.dto.request.UpdateRootBranchRequest;
import com.familyfund.modules.genealogy.dto.response.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.response.BranchTreeNodeResponse;

public interface GenealogyService {

    BranchDetailResponse getRootBranch();

    BranchDetailResponse updateRootBranch(UpdateRootBranchRequest request);

    BranchDetailResponse createChildBranch(CreateChildBranchRequest request);

    BranchDetailResponse updateBranch(String branchId, UpdateBranchRequest request);

    BranchTreeNodeResponse getBranchTree();

    void deleteBranch(String branchId);

    void assignMemberToBranch(String memberId, String branchId);
}
