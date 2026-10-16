package com.familyfund.modules.genealogy.dto;

import com.familyfund.modules.genealogy.entity.FamilyBranch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDetailResponse {

    private String branchId;
    private String clanName;
    private String branchName;
    private Integer generationLevel;
    private String parentBranchId;
    private String representativeId;
    private String representativeName;
    private String description;
    private Instant createdAt;

    public static BranchDetailResponse fromEntity(FamilyBranch entity) {
        if (entity == null) {
            return null;
        }
        return BranchDetailResponse.builder()
                .branchId(entity.getBranchId())
                .clanName(entity.getClanName())
                .branchName(entity.getBranchName())
                .generationLevel(entity.getGenerationLevel())
                .parentBranchId(entity.getParentBranch() != null ? entity.getParentBranch().getBranchId() : null)
                .representativeId(entity.getRepresentative() != null ? entity.getRepresentative().getMemberId() : null)
                .representativeName(entity.getRepresentative() != null ? entity.getRepresentative().getFullName() : null)
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
