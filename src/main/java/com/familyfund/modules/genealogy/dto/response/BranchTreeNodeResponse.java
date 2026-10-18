package com.familyfund.modules.genealogy.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record BranchTreeNodeResponse(
        String branchId,
        String clanId,
        String branchName,
        String clanName,
        Integer generationLevel,
        String parentBranchId,
        String representativeId,
        String representativeName,
        int memberCount,
        List<BranchTreeNodeResponse> children
) {
    public BranchTreeNodeResponse {
        if (children == null) {
            children = List.of();
        }
    }
}
