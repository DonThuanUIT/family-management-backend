package com.familyfund.modules.genealogy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchTreeNodeResponse {

    private String branchId;
    private String branchName;
    private String clanName;
    private Integer generationLevel;
    private String parentBranchId;
    private String representativeId;
    private String representativeName;
    private int memberCount;

    @Builder.Default
    private List<BranchTreeNodeResponse> children = new ArrayList<>();
}
