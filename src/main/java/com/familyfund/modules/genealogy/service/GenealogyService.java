package com.familyfund.modules.genealogy.service;

import com.familyfund.modules.genealogy.dto.BranchDetailResponse;
import com.familyfund.modules.genealogy.dto.CreateChildBranchRequest;
import com.familyfund.modules.genealogy.dto.UpdateRootBranchRequest;

public interface GenealogyService {

    BranchDetailResponse getRootBranch();

    BranchDetailResponse updateRootBranch(UpdateRootBranchRequest request);

    BranchDetailResponse createChildBranch(CreateChildBranchRequest request);
}
